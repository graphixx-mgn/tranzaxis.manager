package codex.component.messagebox;

import codex.component.dialog.Dialog;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.FocusManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Окно информационных/предупреждающих сообшений.
 */
public final class MessageBox extends Dialog {
    
    public static void show(MessageType type, String text) {
        new MessageBox(type, null, text, null).setVisible(true);
    }
    
    public static void show(MessageType type, String title, String text) {
        new MessageBox(type, title, text, null).setVisible(true);
    }
    
    public static void show(MessageType type, String title, String text, ActionListener close) {
        new MessageBox(type, title, text, close).setVisible(true);
    }
    
    /**
     * Конструктор окна.
     * @param parent Указатель на родительское окна для относительного позиционирования на экране.
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
                new MessagePanel(type, text), 
                close, 
                buttonSet(type)
        );
        setResizable(false);
    }
    
    static final Dialog.Default[] buttonSet(MessageType type) {
        switch (type) {
            case INFORMATION:
                return new Dialog.Default[]{Dialog.Default.BTN_OK};
            case CONFIRMATION:
                return new Dialog.Default[]{Dialog.Default.BTN_OK, Dialog.Default.BTN_CANCEL};
            default:
                return new Dialog.Default[]{Dialog.Default.BTN_CLOSE};
        }
    }
    
    static final class MessagePanel extends JPanel {
    
        public MessagePanel(MessageType type, String text) {
            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(10, 20, 10, 20));

            final JLabel iconLabel = new JLabel(type.getIcon());
            iconLabel.setVerticalAlignment(JLabel.TOP);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 20));
            add(iconLabel, BorderLayout.WEST);
            
            final JLabel messageLabel = new JLabel("<html>"+text.replaceAll("\n", "<br>")+"</html>");
            add(messageLabel, BorderLayout.CENTER);
        }
    }
    
}
