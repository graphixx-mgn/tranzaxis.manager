package codex.notification;

import codex.component.button.IButton;
import codex.component.button.PushButton;
import codex.component.panel.HTMLView;
import codex.editor.IEditor;
import codex.mask.DateFormat;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.service.Service;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

class MessageView extends JPanel {

    private final static int  IMAGE_SIZE   = (int) (IEditor.FONT_VALUE.getSize()*1.3f);
    private final static Font FONT_SUBJECT = IEditor.FONT_VALUE.deriveFont(Font.BOLD);
    private final static Font FONT_TIME    = IEditor.FONT_VALUE.deriveFont(IEditor.FONT_VALUE.getSize()*.7f);
    private final static String ACTION     = Language.get("actions");

    private final Message message;
    private final MouseListener hoverListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
            setBorder(getViewBorder(getBorderColor(message.getSeverity()), 6));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            setBorder(getViewBorder(getBorderColor(message.getSeverity()), 3));
        }
    };
    private final ReadHandler readHandler = new ReadHandler();

    MessageView(Message message) {
        super(new BorderLayout());
        this.message = message;

        Message.Severity severity = message.getSeverity();
        List<Message.IMessageAction> actions = message.getActions();

        JComponent header = getMessageHeader(message);
        JComponent body = getMessageBody(message);
        JComponent footer = actions.isEmpty() ? Box.createHorizontalBox() : getMessageFooter(actions);

        add(header, BorderLayout.NORTH);
        add(body,   BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        setBorder(getViewBorder(getBorderColor(severity), 3));
        addMouseListener(hoverListener);
    }

    ReadHandler getReadHandler() {
        return readHandler;
    }

    private JPanel getMessageHeader(Message message) {
        Date   created = message.getCreated();
        String subject = message.getSubject();
        Message.Severity severity = message.getSeverity();

        JLabel time = new JLabel(MessageFormat.format(
                "{0} {1}",
                DateFormat.Date.newInstance().getFormat().format(created),
                DateFormat.Time.newInstance().getFormat().format(created)
        )) {{
            setFont(FONT_TIME);
            setVerticalAlignment(SwingConstants.TOP);
        }};

        JLabel status = new JLabel(
                null,
                message.getStatus().getBadge(),
                SwingConstants.CENTER
        );
        status.addMouseListener(readHandler);
        status.addMouseListener(hoverListener);

        JLabel title = new JLabel(
                subject,
                severity.getIcon() != null ? ImageUtils.resize(severity.getIcon(), IMAGE_SIZE, IMAGE_SIZE) : null,
                SwingConstants.LEFT
        ) {{
            setBorder(new EmptyBorder(3,3,3,3));
            setFont(FONT_SUBJECT);
            setIconTextGap(10);
        }};

        CancelButton doRemove = new CancelButton();
        doRemove.addActionListener(e -> Entity.deleteInstance(message, false, true));

        message.model.addModelListener(new IModelListener() {
            @Override
            public void modelChanged(EntityModel model, List<String> changes) {
                if (changes.contains(Message.PROP_STATUS)) {
                    message.onStatusChange(
                            (Message.MessageStatus) message.model.getValue(Message.PROP_STATUS),
                            (Message.MessageStatus) message.model.getUnsavedValue(Message.PROP_STATUS)
                    );
                    status.setIcon(message.getStatus().getBadge());
                }
            }
        });
        return new JPanel(new BorderLayout()) {{
            Color titleColor = getTitleColor(severity);
            setBackground(titleColor);
            add(title, BorderLayout.WEST);
            add(new JPanel() {
                {
                    setOpaque(false);
                    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                    add(time);
                    add(Box.createRigidArea(new Dimension(5, 0)));
                    add(status);
                    add(Box.createRigidArea(new Dimension(5, 0)));
                    add(doRemove);
                    add(Box.createRigidArea(new Dimension(5, 0)));
                }
            },  BorderLayout.EAST);
        }};
    }

    private JPanel getMessageBody(Message message) {
        String content = message.getContent();

        HTMLView body = new HTMLView();
        body.setContentType("text/html");
        body.setFont(IEditor.FONT_NORMAL);
        body.setText(content);
        body.addMouseListener(hoverListener);

        return new JPanel(new BorderLayout()) {{
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(0, 5, 0, 5));
            add(body, BorderLayout.CENTER);
        }};
    }

    private JPanel getMessageFooter(List<Message.IMessageAction> actions) {
        return new JPanel() {{
            setBackground(Color.WHITE);
            setLayout(new GridLayout(0, 1, 0, 0));
            setBorder(new CompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    new CompoundBorder(
                            new TitledBorder(new LineBorder(Color.GRAY, 1), ACTION),
                            new EmptyBorder(0, 3, 3, 3)
                    )
            ));
            actions.forEach(action -> add(new JPanel() {{
                setOpaque(true);
                setBackground(null);
                setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                PushButton doAction = new PushButton(
                    ImageUtils.resize(action.getIcon(), IMAGE_SIZE, IMAGE_SIZE),
                    null
                );
                doAction.addActionListener(e -> action.doAction());
                doAction.setBackground(null);

                JLabel descAction = new JLabel(action.getTitle());
                descAction.setBorder(new EmptyBorder(0, 3, 0, 0));

                add(doAction);
                add(descAction);
            }}));
        }};
    }

    private Border getViewBorder(Color typeColor, int margin) {
        return new CompoundBorder(
                new EmptyBorder(0, 0, 5, 0),
                new CompoundBorder(
                        new MatteBorder(0, 0, 1, 1, Color.decode("#DDDDDD")),
                        new CompoundBorder(
                                new MatteBorder(0, margin, 0, 0, typeColor),
                                new EmptyBorder(0, 6-margin, 0, 0)
                        )
                )
        );
    }

    private Color getBorderColor(Message.Severity severity) {
        switch (severity) {
            case Feature:     return Color.decode("#299D37");
            case Information: return Color.decode("#90C8F6");
            case Warning:     return Color.decode("#F69020");
            case Error:       return Color.decode("#DE5347");
            default:          return Color.GRAY;
        }
    }

    private Color getTitleColor(Message.Severity severity) {
        return blend(getBorderColor(severity), Color.WHITE);
    }

    private static Color blend(Color c0, Color c1) {
        double totalAlpha = c0.getAlpha() + c1.getAlpha();
        double weight0 = c0.getAlpha() / totalAlpha;
        double weight1 = c1.getAlpha() / totalAlpha;

        double r = weight0 * c0.getRed() + weight1 * c1.getRed();
        double g = weight0 * c0.getGreen() + weight1 * c1.getGreen();
        double b = weight0 * c0.getBlue() + weight1 * c1.getBlue();
        double a = Math.max(c0.getAlpha(), c1.getAlpha());

        return new Color((int) r, (int) g, (int) b, (int) a);
    }


    final class CancelButton extends JButton implements IButton {

        private final ImageIcon activeIcon;
        private final ImageIcon passiveIcon;

        CancelButton() {
            super();
            this.activeIcon  = ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), IMAGE_SIZE, IMAGE_SIZE);
            this.passiveIcon = ImageUtils.grayscale(this.activeIcon);
            setIcon(passiveIcon);
            setFocusable(false);
            setFocusPainted(false);
            setOpaque(false);
            setBackground(new Color(0, true));
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            addMouseListener(hoverListener);

            getModel().addChangeListener((ChangeEvent event) -> {
                ButtonModel model = (ButtonModel) event.getSource();
                if (model.isRollover()) {
                    setIcon(activeIcon);
                } else {
                    setIcon(passiveIcon);
                }
            });
        }

        @Override
        public void setHint(String text) {
            setToolTipText(text);
        }

        @Override
        public void click() {
            doClick();
        }

        @Override
        public void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }


    class ReadHandler extends MouseAdapter {

        private ReadTrigger getReadTrigger() {
            String triggerName = Service.getProperty(NotificationService.class, NotifyServiceOptions.PROP_READTRIGGER);
            return triggerName != null ? ReadTrigger.valueOf(triggerName) : ReadTrigger.OnClick;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (message.getStatus() != Message.MessageStatus.New) return;
            if (getReadTrigger() == ReadTrigger.OnClick)
                message.setStatus(Message.MessageStatus.Read);
        }

        void ancestorEvent(JComponent ancestor) {
            message.onShow();
            if (message.getStatus() != Message.MessageStatus.New) return;
            int ownerHeight = ancestor.getHeight();
            if (ownerHeight > 0 && getReadTrigger() == ReadTrigger.OnShow) {
                int viewHeight  = getHeight();
                int shownHeight = getVisibleRect().height;
                if (shownHeight == viewHeight || shownHeight == ownerHeight) {
                    message.setStatus(Message.MessageStatus.Read);
                }
            }
        }
    }

    enum ReadTrigger implements Iconified {
        OnClick(ImageUtils.getByPath("/images/target.png")),
        OnShow(ImageUtils.getByPath("/images/search.png"));

        private final ImageIcon icon;
        private final String    title;

        ReadTrigger(ImageIcon icon) {
            this.icon  = icon;
            this.title = Language.get(MessageView.class, "trigger@"+name().toLowerCase());
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
