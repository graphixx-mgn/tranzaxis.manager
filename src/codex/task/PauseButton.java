package codex.task;

import codex.component.button.IButton;
import codex.utils.ImageUtils;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;

/**
 * Кнопка отмены задачи в менеджере задач.
 */
final class PauseButton extends JButton implements IButton {
    
    private final ImageIcon activeIcon;
    private final ImageIcon passiveIcon;

    PauseButton() {
        super();
        this.activeIcon  = ImageUtils.getByPath("/images/pausetask.png");
        this.passiveIcon = ImageUtils.grayscale(this.activeIcon);
        setIcon(passiveIcon);
        setDisabledIcon(passiveIcon);
        setFocusable(false);
        setFocusPainted(false);
        setOpaque(true);
        setContentAreaFilled(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(0, 5, 0, 0));

        getModel().addChangeListener((ChangeEvent event) -> {
            ButtonModel model = (ButtonModel) event.getSource();
            if (model.isRollover()) {
                setIcon(activeIcon);
            } else {
                setIcon(passiveIcon);
            }
        });
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
