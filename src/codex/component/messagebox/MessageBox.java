package codex.component.messagebox;

import codex.component.dialog.Dialog;
import java.awt.BorderLayout;
import java.awt.Window;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Окно информационных/предупреждающих сообшений.
 */
public final class MessageBox extends Dialog {
    
    /**
     * Конструктор окна.
     * @param parent Указатель на родительское окна для относительного позиционирования на экране.
     * @param type Тип сообщения (см. {@link MessageType).
     * @param title Текст заготовка окна, если NULL - берется по-умолчанию из типа 
     * сообщения.
     * @param text Текст сообщения, поддерживающий перенос строк.
     * @param close Слушатель события закрытия окна.
     */
    public MessageBox(Window parent, MessageType type, String title, String text, Action close) {
        super(
                parent, 
                type.getIcon(), 
                title, 
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
                return new Dialog.Default[]{Dialog.Default.BTN_OK, Dialog.Default.BTN_CLOSE};
            default:
                return new Dialog.Default[]{Dialog.Default.BTN_CLOSE};
        }
    }
    
    static final class MessagePanel extends JPanel {
    
        public MessagePanel(MessageType type, String text) {
            setLayout(new BorderLayout(0, 10));
            setBorder(new EmptyBorder(20, 30, 20, 30));

            final JLabel iconLabel = new JLabel(type.getIcon());
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 30));
            add(iconLabel, BorderLayout.WEST);
            
            final JLabel messageLabel = new JLabel("<html>"+text.replaceAll("\n", "<br>")+"</html>");
            add(messageLabel, BorderLayout.CENTER);
        }
    }
    
}
