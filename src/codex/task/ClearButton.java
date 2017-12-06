package codex.task;

import codex.component.button.IButton;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

/**
 * Кнопка очистки очереди в менеджере задач.
 */
public final class ClearButton extends JButton implements IButton  {
    
    private final static ImageIcon ICON = ImageUtils.resize(ImageUtils.getByPath("/images/remove.png"), 17, 17);

    ClearButton() {
        super();
        setIcon(ICON);
        setDisabledIcon(ImageUtils.grayscale(ICON));
        setFocusPainted(false);
        setOpaque(true);
        setContentAreaFilled(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(0, 5, 0, 0));
    }

    @Override
    public void setHint(String text) {
        setToolTipText(text);
    }
    
    @Override
    public void setInactive(boolean inactive) {}

    @Override
    public boolean isInactive() {
        return false;
    }

    @Override
    public void click() {
        doClick();
    }

}
