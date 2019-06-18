package codex.component.messagebox;

import codex.component.dialog.Dialog;
import codex.editor.IEditor;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Окно информационных/предупреждающих сообшений.
 */
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
                FocusManager.getCurrentManager().getActiveWindow(),
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

            final JLabel messageLabel = new JLabel("<html>"+text.replaceAll("\n", "<br>")+"</html>");
            messageLabel.setFont(IEditor.FONT_VALUE.deriveFont((float) (IEditor.FONT_VALUE.getSize()*0.9)));
            add(messageLabel, BorderLayout.CENTER);
        }

        @Override
        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            super.paintComponent(g);
        }
    }
    
}
