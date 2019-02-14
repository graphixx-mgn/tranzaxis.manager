package codex.component.button;

import codex.utils.ImageUtils;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;

/**
 * Кнопка вторичной команды в группе.
 */
public final class GroupItemButton extends JMenuItem implements IButton {
    
    /**
     * Конструктор кнопки.
     * @param icon Иконка кнопки.
     * @param title Надпись кнопки.
     */
    public GroupItemButton(ImageIcon icon, String title) {
        super(title, icon);
        setDisabledIcon(ImageUtils.grayscale(icon));
    }

    @Override
    public void setHint(String text) {}

    @Override
    public void setInactive(boolean inactive) {
        setEnabled(!inactive);
    }

    @Override
    public boolean isInactive() {
        return !isEnabled();
    }

    @Override
    public void click() {
        doClick();
    }
    
}