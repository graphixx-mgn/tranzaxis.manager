package codex.component.button;

import codex.component.dialog.Dialog;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Кнопка управления диалоговым окном {@link Dialog}.
 */
@ThreadSafe
public final class DialogButton extends PushButton {

    private KeyStroke keyCode;
    private int id;
   
    /**
     * Конструктор кнопки.
     * @param icon Иконка кнопки.
     * @param title Надпись кнопки.
     * @param keyCode Код клавиши клавиатуры (см. константы VK_*** в {@link KeyEvent}).
     * Установить -1, если привязка не требуется.
     * @param id Код возврата кнопки, который можно ьудет анализировать после 
     * закрытия диалога.
     */
    public DialogButton(ImageIcon icon, String title, int keyCode, int id) {
        super(icon, title);
        setIcon(ImageUtils.resize(
                icon,
                (int) (getPreferredSize().height * 0.6), (int) (getPreferredSize().height * 0.6)
        ));
        setLayout(new GridLayout());
        this.keyCode = KeyStroke.getKeyStroke(keyCode, 0);
        this.id = id;
    }
    
    /**
     * Получить текщую привязку кнопки к (в общем случае комбинации) клавише. 
     * Это критерий по которому диспетчер событий клавиатуры постоянно проверяет 
     * новые события на соответствие таким критериям и вызывает соответствующий
     * слушатель окна.
     * @return Объект описывающий состояние клавиш.
     */
    public synchronized KeyStroke getKeyCode() {
        return keyCode;
    }
    
    /**
     * Установить привязку кнопки к клавише клавиатуры.
     * @param keyCode Код клавиши клавиатуры (см. константы VK_*** в {@link KeyEvent}).
     */
    public synchronized void setKeyCode(int keyCode) {
        this.keyCode = KeyStroke.getKeyStroke(keyCode, 0);
    }
    
    /**
     * Получить текущий код возврата кнопки.
     * @return Числовой код.
     */
    public synchronized int getID() {
        return id;
    }
    
    /**
     * Изменить код возврата кнопки. При этом следует следить чтобы кнопки диалога
     * имели разные коды. Коды стандартных кнопок можно посмотреть в {@link Dialog}
     * @param id Числовой код.
     */
    public synchronized void setID(int id) {
        this.id = id;
    }
     
}
