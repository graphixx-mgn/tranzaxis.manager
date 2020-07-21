package codex.notification;

import codex.component.panel.ScrollablePanel;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.supplier.IDataSupplier;
import codex.unit.AbstractUnit;
import codex.unit.IEventInformer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;

public class MessagingQueue extends AbstractUnit implements Serializable, IEventInformer {

    private ScrollablePanel messagesPanel;
    private MessageSupplier supplier = new MessageSupplier();
    private Map<Message, MessageView> messageViews = new LinkedHashMap<>();
    private boolean unitShown = false;
    private final List<IEventListener> eventListeners = new LinkedList<>();

    {
        MessageInbox.getInstance().addListener(message -> readNextPage());
    }

    @Override
    public JComponent createViewport() {
        messagesPanel = new ScrollablePanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.add(Box.createVerticalGlue());
        messagesPanel.setScrollableWidth(ScrollablePanel.ScrollableSizeHint.FIT);

        JScrollPane scrollPane = new JScrollPane(messagesPanel);
        scrollPane.getViewport().setBackground(Color.decode("#F5F5F5"));
        scrollPane.setBorder(new EmptyBorder(2, 2, 1, 2));
        scrollPane.setColumnHeader(null);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (!event.getValueIsAdjusting()) {
                JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
                int extent = scrollBar.getModel().getExtent();
                int maximum = scrollBar.getModel().getMaximum();
                int current = scrollBar.getModel().getValue();

                if (extent + current == maximum && supplier.available(IDataSupplier.ReadDirection.Backward)) {
                    SwingUtilities.invokeLater(() -> {
                        messagesPanel.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        readPrevPage();
                        messagesPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    });
                }
                if (unitShown) {
                    messageViews.forEach((message, view) -> {
                        for (AncestorListener listener : view.getAncestorListeners()) {
                            listener.ancestorAdded(new AncestorEvent(view, 0, scrollPane, scrollPane.getParent()));
                        }
                    });
                }
            }
        });

        scrollPane.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorRemoved(AncestorEvent event) {
                unitShown = false;
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {}

            @Override
            public void ancestorAdded(AncestorEvent event) {
                scrollPane.getViewport().setViewPosition(new Point(0,0));
                unitShown = true;
            }
        });

        return new JPanel(new BorderLayout()) {{
            add(scrollPane, BorderLayout.CENTER);
        }};
    }

    @Override
    public void viewportBound() {
        super.viewportBound();
    }

    private void readPrevPage() {
        SwingUtilities.invokeLater(() -> {
            List<Message> messages = supplier.getPrev();
            messages.forEach(message -> messagesPanel.add(addMessageView(message)));
            if (!messages.isEmpty()) {
                messagesPanel.revalidate();
                messagesPanel.repaint();
            }
        });
    }

    private void readNextPage() {
        SwingUtilities.invokeLater(() -> {
            List<Message> messages = supplier.getNext();
            messages.forEach(message -> messagesPanel.add(addMessageView(message), 0));
            if (!messages.isEmpty()) {
                messagesPanel.revalidate();
                messagesPanel.repaint();
            }
        });
    }

    MessageView addMessageView(Message message) {
        MessageView view = new MessageView(message);
        messageViews.put(message, view);
        message.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(Message.PROP_STATUS)) {
                    showEvents();
                }
            }

            @Override
            public void modelDeleted(EntityModel model) {
                MessageView view = messageViews.remove(message);
                messagesPanel.remove(view);
                messagesPanel.revalidate();
                messagesPanel.repaint();
            }
        });
        showEvents();
        return view;
    }

    @Override
    public void addEventListener(IEventListener listener) {
        eventListeners.add(listener);
    }

    @Override
    public void removeEventListener(IEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public List<IEventListener> getEventListeners() {
        return new LinkedList<>(eventListeners);
    }

    private void showEvents() {
        SwingUtilities.invokeLater(() -> {
            long newMessages = messageViews.keySet().stream()
                    .filter(msg -> msg.getStatus().equals(Message.MessageStatus.New))
                    .count();
            getEventListeners().forEach(listener -> listener.showEvents((int) newMessages));
        });
    }
}
