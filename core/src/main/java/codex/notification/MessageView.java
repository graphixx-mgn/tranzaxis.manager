package codex.notification;

import codex.component.button.IButton;
import codex.component.panel.HTMLView;
import codex.editor.IEditor;
import codex.log.Logger;
import codex.mask.DateFormat;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.utils.ImageUtils;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

class MessageView extends JPanel {

    private final static int  IMAGE_SIZE   = (int) (IEditor.FONT_VALUE.getSize()*1.3f);
    private final static Font FONT_SUBJECT = IEditor.FONT_VALUE.deriveFont(Font.BOLD);
    private final static Font FONT_TIME    = IEditor.FONT_VALUE.deriveFont(IEditor.FONT_VALUE.getSize()*.7f);

    private final Message message;
    private final JPanel body, header;
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

    MessageView(Message message) {
        super(new BorderLayout());
        this.message = message;

        Message.Severity severity = message.getSeverity();
        //Message.IMessageAction action = message.getAction();

        header = getMessageHeader(message);
        body   = getMessageBody(message);
        add(header, BorderLayout.NORTH);
        add(body,   BorderLayout.CENTER);
        //add(getMessageFooter(message), BorderLayout.SOUTH);


        //if (action != null) {
//            PushButton doAction = new PushButton(
//                    ImageUtils.resize(IMAGE_READ/*action.getIcon()*/, IMAGE_SIZE, IMAGE_SIZE),
//                    /*action.getTitle()*/"TEST"
//            );
//            doAction.setBackground(null);
            //doAction.addActionListener(e -> action.doAction());
//            add(new Box(BoxLayout.X_AXIS) {{
//                add(doAction);
//                setBorder(new EmptyBorder(5, margin.get(), 0, 0));
//            }}, BorderLayout.SOUTH);
        //}

        setBackground(Color.WHITE);
        setBorder(getViewBorder(getBorderColor(severity), 3));
        addMouseListener(hoverListener);
        new ReadHandler();
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
        doRemove.addActionListener(e -> {
            if (message.getStatus() != Message.MessageStatus.Deleted) {
                message.setStatus(Message.MessageStatus.Deleted);
            } else {
                message.delete();
            }
        });

        message.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(Message.PROP_STATUS)) {
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
            setOpaque(false);
            add(body, BorderLayout.CENTER);
        }};
    }

//    private JPanel getMessageFooter(Message message) {
//        return new JPanel() {{
//            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
//            setBorder(new LineBorder(Color.GREEN, 1));
//        }};
//    }

    private Border getViewBorder(Color typeColor, int margin) {
        return new CompoundBorder(
                new EmptyBorder(0, 0, 5, 0),
                new CompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Color.decode("#EEEEEE")),
                        new CompoundBorder(
                                new MatteBorder(0, margin, 0, 0, typeColor),
                                new EmptyBorder(0, 6-margin, 0, 0)
                        )
                )
        );
    }

    private Color getBorderColor(Message.Severity severity) {
        switch (severity) {
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


    interface IReadHandler extends MouseListener {

        void messageSelected();

        @Override
        default void mouseClicked(MouseEvent e) {}

        @Override
        default void mousePressed(MouseEvent e) {}

        @Override
        default void mouseReleased(MouseEvent e) {}

        @Override
        default void mouseEntered(MouseEvent e) {
            messageSelected();
        }

        @Override
        default void mouseExited(MouseEvent e) {}
    }


    private class ReadHandler implements IReadHandler {

        private final Consumer<Message> reader = message -> message.setStatus(Message.MessageStatus.Read);

        ReadHandler() {
            if (MessageView.this.message.getStatus() == Message.MessageStatus.New) {
                attach();
            }
        }

        private void attach() {
            MessageView.this.addMouseListener(this);
            MessageView.this.body.addMouseListener(this);
        }

        private void detach() {
            MessageView.this.removeMouseListener(this);
            MessageView.this.body.removeMouseListener(this);
        }

        @Override
        public void messageSelected() {
            reader.accept(MessageView.this.message);
            detach();
        }
    }
}
