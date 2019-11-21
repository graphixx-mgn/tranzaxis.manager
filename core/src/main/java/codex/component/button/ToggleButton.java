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
     * Установить состояние переключателя.
     * @param checked TRUE - если включен, иначе - выключен.
     */
    public final void setChecked(boolean checked) {
        if (checked != isChecked()) {
            click();
        }
        this.checked = checked;
        redraw();
    }
    
    /**
     * Проверка текущего состояния переключателя.
     * @return TRUE - если включен, иначе - выключен.
     */
    public final boolean isChecked() {
        return checked;
    }
    
    /**
     * Установить состояние переключателя на противоположное текущему.
     */
    public final void toggle() {
        setChecked(!isChecked());
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
        setBorder(checked ? new MatteBorder(0, 0, 4, 0, Color.decode("#90C8F6")) : new MatteBorder(0, 0, 4, 0, Color.GRAY));
        if (icon != null) {
            button.setIcon(checked ? icon : ImageUtils.grayscale(icon));
        }
    }
    
}
