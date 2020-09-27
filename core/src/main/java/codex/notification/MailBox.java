package codex.notification;

import codex.component.panel.ScrollablePanel;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.presentation.AncestorAdapter;
import codex.presentation.SelectorPresentation;
import codex.supplier.IDataSupplier;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MailBox extends AbstractUnit {

    private static final ImageIcon ICON_INBOX = ImageUtils.combine(
            ImageUtils.getByPath("/images/notify.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), .7f),
            SwingConstants.SOUTH_EAST
    );

    private static final MailBox INSTANCE = new MailBox();
    public  static MailBox getInstance() {
        return INSTANCE;
    }

    private final Queue queue = new Queue();

    private MailBox() {
        MessageInbox.getInstance().addListener(queue::postMessage);
    }

    @Override
    public JComponent createViewport() {
        JTabbedPane tabbedPanel = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPanel.setFocusable(false);
        tabbedPanel.setBorder(new MatteBorder(1, 0, 1, 0, Color.GRAY));
        for (INode child : queue.childrenList()) {
            MailFolder folder = (MailFolder) child;
            tabbedPanel.addTab(
                    folder.getTitle(),
                    ImageUtils.resize(folder.getIcon(), 24, 24),
                    folder.getSelectorPresentation()
            );
        }
        return tabbedPanel;
    }


    private class Queue extends Catalog {

        private Inbox inbox = new Inbox();

        Queue() {
            super(null, null, "Mail",null);
            attach(inbox);
        }

        private void postMessage(Message message) {
            for (INode child : childrenList()) {
                MailFolder folder = (MailFolder) child;
                if (folder.getCondition().test(message)) {
                    folder.moveMessages(Collections.singletonList(message));
                    return;
                }
            }
            Logger.getContextLogger(NotificationService.class).error(
                    "Suitable folder for message {0} not found",
                    message
            );
        }
    }


    private abstract class MailFolder extends Catalog {

        protected final MessageSupplier supplier = new MessageSupplier();
        private final FolderView folderView = new FolderView(this);
        private final Predicate<Message> condition;

        private MailFolder(ImageIcon icon, String title, Predicate<Message> condition) {
            super(null, icon, title, null);
            this.condition = condition;
        }

        private Predicate<Message> getCondition() {
            return condition;
        }

        protected synchronized void moveMessages(List<Message> messages) {
            List<Message> filtered = messages.parallelStream()
                    .filter(message -> getMessageIndex(message) < 0)
                    .collect(Collectors.toList());

            filtered.forEach(message -> {
                if (message.getParent() == null) {
                    // Is new incoming message
                    message.model.addModelListener(new IModelListener() {
                        @Override
                        public void modelChanged(EntityModel model, List<String> changes) {
                            if (changes.contains(Message.PROP_STATUS)) {
                                if (!getCondition().test(message)) {
                                    queue.postMessage(message);
                                }
                            }
                        }

                        @Override
                        public void modelSaved(EntityModel model, List<String> changes) {
                            if (changes.contains(Message.PROP_STATUS)) {
                                messageUpdated(message);
                            }
                        }

                        @Override
                        public void modelDeleted(EntityModel model) {
                            messageUpdated(message);
                        }
                    });
                } else {
                    message.getParent().detach(message);
                }
                attach(message);
                List<Long> sequences = childrenList().stream()
                        .map(iNode -> ((Message) iNode).getTime())
                        .sorted(((Comparator<Long>) Long::compare).reversed())
                        .collect(Collectors.toList());
                move(message, sequences.indexOf(message.getTime()));
            });
            folderView.messagesAdded(filtered);
        }

        @Override
        public void detach(INode child) {
            Message msg = (Message) child;
            folderView.messageRemoved(msg);
            super.detach(child);
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        protected void messageUpdated(Message message) {}

        @Override
        public Class<? extends Entity> getChildClass() {
            return Message.class;
        }

        @Override
        public void loadChildren() {
            SelectorPresentation presentation = getSelectorPresentation();
            if (presentation != null) {
                presentation.removeAll();
                presentation.add(folderView);
            }
        }

        private void readPrevPage() {
            List<Message> messages = supplier.getPrev();
            if (!messages.isEmpty()) {
                moveMessages(messages);
            }
        }

        private int getMessageIndex(Message message) {
            int count = getChildCount();
            for (int idx = 0; idx < count ; idx ++) {
                if (getChildAt(idx).equals(message)) {
                    return idx;
                }
            }
            return -1;
        }
    }


    private class Inbox extends MailFolder {

        private Inbox() {
            super(
                    ICON_INBOX,
                    Language.get(MailBox.class, "inbox.title"),
                    message ->
                            message.getStatus() == Message.MessageStatus.New ||
                            message.getStatus() == Message.MessageStatus.Read
            );
            this.supplier.setFilter(MessageFormat.format("[status] IS NULL OR [status] == ''{0}''", Message.MessageStatus.Read));
        }

        @Override
        protected synchronized void moveMessages(List<Message> messages) {
            super.moveMessages(messages);
            showUnreadMessages();
        }

        @Override
        protected void messageUpdated(Message message) {
            showUnreadMessages();
        }

        private void showUnreadMessages() {
            SwingUtilities.invokeLater(() -> {
                int unreadMessages = supplier.getUnreadMessages();
                getEventListeners().forEach(listener -> listener.showEvents(unreadMessages));
            });
        }
    }


    private class FolderView extends JPanel {

        private   final MailFolder folder;
        private   final ScrollablePanel messagesPanel;

        FolderView(MailFolder folder) {
            super(new BorderLayout());
            this.folder = folder;

            messagesPanel = new ScrollablePanel() {
                @Override
                public void scrollRectToVisible(Rectangle rect) {}
            };
            messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
            messagesPanel.setScrollableWidth(ScrollablePanel.ScrollableSizeHint.FIT);

            JScrollPane scrollPane = new JScrollPane(messagesPanel);
            scrollPane.getViewport().setBackground(Color.decode("#F5F5F5"));
            scrollPane.setBorder(new CompoundBorder(
                    new EmptyBorder(2, 2, 2, 2),
                    new MatteBorder(0, 0, 0, 0, Color.GRAY)
            ));
            scrollPane.setColumnHeader(null);

            Runnable onShow = () -> {
                if (getViewport().isDisplayable() && getViewport().isShowing() && getViewport().isVisible()) {
                    for (Component c : messagesPanel.getComponents()) {
                        ((MessageView) c).getReadHandler().ancestorEvent(scrollPane.getViewport());
                    }
                }
            };

            scrollPane.getVerticalScrollBar().addAdjustmentListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
                    int extent = scrollBar.getModel().getExtent();
                    int maximum = scrollBar.getModel().getMaximum();
                    int current = scrollBar.getModel().getValue();

                    if (extent + current == maximum && folder.supplier.available(IDataSupplier.ReadDirection.Backward)) {
                        SwingUtilities.invokeLater(() -> {
                            scrollPane.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                            folder.readPrevPage();
                            scrollPane.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        });
                    }
                    onShow.run();
                }
            });
            scrollPane.addAncestorListener(new AncestorAdapter() {
                @Override
                public void ancestorAdded(AncestorEvent e) {
                    super.ancestorAdded(e);
                    onShow.run();
                }
            });

            add(scrollPane, BorderLayout.CENTER);
        }

        private void messagesAdded(List<Message> messages) {
            messages.forEach(message -> {
                int pos = folder.getMessageIndex(message);
                messagesPanel.add(
                        new MessageView(message),
                        pos
                );
            });
            messagesPanel.revalidate();
            messagesPanel.repaint();
        }

        private void messageRemoved(Message message) {
            messagesPanel.remove(folder.getMessageIndex(message));
            messagesPanel.revalidate();
            messagesPanel.repaint();
        }
    }
}
