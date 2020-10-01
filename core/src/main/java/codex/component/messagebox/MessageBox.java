package codex.component.messagebox;

import codex.component.dialog.Dialog;
import codex.component.ui.StripedProgressBarUI;
import codex.editor.IEditor;
import codex.task.TaskView;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Окно информационных/предупреждающих сообшений.
 */
@ThreadSafe
public final class MessageBox extends Dialog {
    
    /**
     * Отобразить окно сообщения.
     * @param type Тип сообщения {@link MessageType}
     * @param text Текст сообщения.
     */
    public static void show(MessageType type, String text) {
        new MessageBox(type, null, text, null).setVisible(true);
    }
    
    /**
     * Отобразить окно сообщения.
     * @param type Тип сообщения {@link MessageType}
     * @param title Текст заголовка окна.
     * @param text Текст сообщения.
     */
    public static void show(MessageType type, String title, String text) {
        new MessageBox(type, title, text, null).setVisible(true);
    }
    
    /**
     * Отобразить окно сообщения.
     * @param type Тип сообщения {@link MessageType}
     * @param title Текст заголовка окна.
     * @param text Текст сообщения.
     * @param close Обработчик закрытия окна или нажатия кнопок.
     */
    public static void show(MessageType type, String title, String text, ActionListener close) {
        new MessageBox(type, title, text, close).setVisible(true);
    }

    public static boolean confirmation(String title, String text) {
        final AtomicBoolean result = new AtomicBoolean(false);
        new MessageBox(
                MessageType.CONFIRMATION,
                title, text,
                event -> result.set(event.getID() == Dialog.OK)
        ).setVisible(true);
        return result.get();
    }

    public static boolean confirmation(ImageIcon icon, String title, String text) {
        final AtomicBoolean result = new AtomicBoolean(false);
        new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                MessageType.CONFIRMATION.getIcon(),
                title,
                new MessagePanel(icon, text),
                event -> result.set(event.getID() == Dialog.OK),
                buttonSet(MessageType.CONFIRMATION)
        ).setVisible(true);
        return result.get();
    }

    public static ProgressDialog progressDialog(ImageIcon icon, String title) {
        final AtomicBoolean canceled = new AtomicBoolean(false);
        return new ProgressDialog(icon, title, e -> canceled.set(true)) {
            @Override
            public boolean isCanceled() {
                return canceled.get();
            }

            @Override
            public void setVisible(boolean visible) {
                if (visible) {
                    canceled.set(false);
                }
                super.setVisible(visible);
            }
        };
    }
    
    /**
     * Конструктор окна.
     * @param type Тип сообщения (см. {@link MessageType).
     * @param title Текст заготовка окна, если NULL - берется по-умолчанию из типа 
     * сообщения.
     * @param text Текст сообщения, поддерживающий перенос строк.
     * @param close Слушатель события закрытия окна.
     */
    private MessageBox(MessageType type, String title, String text, ActionListener close) {
        super(
                Dialog.findNearestWindow(),
                type.getIcon(), 
                title != null ? title : type.toString(), 
                new MessagePanel(type.getIcon(), text),
                close, 
                buttonSet(type)
        );
        setResizable(false);
    }
    
    private static Dialog.Default[] buttonSet(MessageType type) {
        switch (type) {
            case INFORMATION:
                return new Dialog.Default[]{Dialog.Default.BTN_OK};
            case CONFIRMATION:
                return new Dialog.Default[]{Dialog.Default.BTN_OK, Dialog.Default.BTN_CANCEL};
            default:
                return new Dialog.Default[]{Dialog.Default.BTN_CLOSE};
        }
    }


    private static final class MessagePanel extends JPanel {

        MessagePanel(ImageIcon icon, String text) {
            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 20, 10, 20));

            final JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(JLabel.TOP);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 20));
            add(iconLabel, BorderLayout.WEST);

            final ImageUtils.HTMLToolKit toolKit = new ImageUtils.HTMLToolKit();
            final JEditorPane pane = new JEditorPane() {
                @Override
                public void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    super.paintComponent(g2);
                }
            };
            pane.setEditorKit(toolKit);
            pane.setOpaque(false);
            pane.setContentType("text/html");
            pane.setEditable(false);
            if (text == null) {
                pane.setText("");
            } else {
                pane.setText("<html>" + text.replaceAll("\n", "<br>") + "</html>");
            }
            pane.setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
            pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);

            add(pane, BorderLayout.CENTER);
        }

        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            super.paintComponent(g);
        }
    }


    public static abstract class ProgressDialog extends Dialog {

        private final JProgressBar progress = new JProgressBar() {{
            setMaximum(100);
            setIndeterminate(true);
            setUI(new StripedProgressBarUI(true));
            setForeground(StripedProgressBarUI.PROGRESS_INFINITE);
            setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
            setStringPainted(true);
            setString(TaskView.formatDuration(0));
        }};

        private final JLabel iconLabel = new JLabel() {{
            setVerticalAlignment(JLabel.TOP);
            setBorder(new EmptyBorder(0, 0, 0, 10));
        }
            @Override
            public void setIcon(Icon icon) {
                if (icon instanceof ImageIcon) {
                    int size = progress.getPreferredSize().height;
                    super.setIcon(ImageUtils.resize((ImageIcon) icon, size, size));
                }
            }
        };

        private final JLabel messLabel = new JLabel() {{
            setVerticalAlignment(JLabel.BOTTOM);
            setForeground(IEditor.COLOR_DISABLED);
            setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
        }};

        private final JPanel content = new JPanel(new BorderLayout(0, 5)) {{
            setBorder(new EmptyBorder(10, 20, 10, 20));
            add(iconLabel, BorderLayout.WEST);
            add(messLabel, BorderLayout.CENTER);
            add(progress,  BorderLayout.SOUTH);
        }};

        private LocalDateTime startTime;
        private final Timer updater = new Timer(1000, (ActionEvent event) -> progress.setString(
                TaskView.formatDuration(Duration.between(
                        startTime,
                        LocalDateTime.now()
                ).toMillis())
        ));

        ProgressDialog(ImageIcon icon, String title, ActionListener closeListener) {
            super(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    MessageType.WAITING.getIcon(),
                    title,
                    new JPanel(),
                    closeListener,
                    Dialog.Default.BTN_CANCEL
            );
            iconLabel.setIcon(icon);
            setContent(content);
        }

        @Override
        public void setVisible(boolean visible) {
            SwingUtilities.invokeLater(() -> {
                if (visible) {
                    if (progress.isIndeterminate()) {
                        startTime = LocalDateTime.now();
                        updater.start();
                    }
                } else {
                    if (progress.isIndeterminate()) {
                        updater.stop();
                    }
                }
                super.setVisible(visible);
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension def = super.getPreferredSize();
            return new Dimension(300, def.height);
        }

        public void setDescription(String text) {
            setProgress(progress.getValue(), text);
        }

        public void setProgress(int percent, String text) {
            if (percent > 100 || percent < 0) {
                throw new IllegalStateException("Invalid progress value: "+percent);
            }
            SwingUtilities.invokeLater(() -> {
                progress.setValue(percent);
                messLabel.setText("<html>"+text.replaceAll("\n", "<br>")+"</html>");
            });
        }

        public abstract boolean isCanceled();
    }
    
}
