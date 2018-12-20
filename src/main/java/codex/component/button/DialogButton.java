package codex.component.button;

import codex.component.dialog.Dialog;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

/**
 * Кнопка управления диалоговым окном {@link Dialog}.
 */
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
    public KeyStroke getKeyCode() {
        return keyCode;
    }
    
    /**
     * Установить привязку кнопки к клавише клавиатуры.
     * @param keyCode Код клавиши клавиатуры (см. константы VK_*** в {@link KeyEvent}).
     */
    public void setKeyCode(int keyCode) {
        this.keyCode = KeyStroke.getKeyStroke(keyCode, 0);
    }
    
    /**
     * Получить текущий код возврата кнопки.
     * @return Числовой код.
     */
    public int getID() {
        return id;
    }
    
    /**
     * Изменить код возврата кнопки. При этом следует следить чтобы кнопки диалога
     * имели разные коды. Коды стандартных кнопок можно посмотреть в {@link Dialog}
     * @param id Числовой код.
     */
    public void setID(int id) {
        this.id = id;
    }
     
}
