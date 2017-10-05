package codex.component.button;

import codex.utils.ImageUtils;
import java.awt.Color;
import javax.swing.ImageIcon;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;

/**
 * Кнопка-переключель, котрая имеет два стабильных состояния: включена или выключена.
 */
public class ToggleButton extends PushButton {
    
    private boolean checked = false;
    private final ImageIcon icon;

    /**
     * Конструктор экземпляра кнопки.
     * @param icon Иконка устанавливаемая на кнопку, может быть NULL, если требуется 
     * создать кнопку только с текстом.
     * @param title Поддпись кнопки, может быть NULL, если требуется создать кнопку 
     * только с иконкой.
     * @param checked Начальное состояние, TRUE - если включена, инае - выключена.
     */
    public ToggleButton(ImageIcon icon, String title, boolean checked) {
        super(icon, title);
        this.checked = checked;
        this.icon    = icon;
        redraw();
    }
    
    /**
     * Проверка текущего состояния кнопки.
     * @return TRUE - если включена, инае - выключена.
     */
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void stateChanged(ChangeEvent event) {
        if (button.getModel().isPressed()) {
            checked = !checked;
        }
        super.stateChanged(event);
    }

    @Override
    protected final void redraw() {
        setBackground(checked ? null : Color.decode("#DDDDDD"));
        setBorder(checked ? new MatteBorder(0, 0, 4, 0, PRESS_COLOR) : new MatteBorder(0, 0, 4, 0, Color.GRAY));
        if (icon != null) {
            button.setIcon(checked ? icon : ImageUtils.grayscale(icon));
        }
    }
    
}
