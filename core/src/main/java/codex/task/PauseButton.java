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
    
    private static final ImageIcon ICON_PAUSE  = ImageUtils.getByPath("/images/pausetask.png");
    private static final ImageIcon ICON_RESUME = ImageUtils.getByPath("/images/resumetask.png");
    
    private ImageIcon activeIcon;
    private ImageIcon passiveIcon;

    PauseButton() {
        super();
        this.activeIcon  = ICON_PAUSE;
        this.passiveIcon = ImageUtils.grayscale(ICON_PAUSE);
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
    
    public void setState(boolean paused) {
        this.activeIcon  = paused ? ICON_RESUME : ICON_PAUSE;
        this.passiveIcon = ImageUtils.grayscale(paused ? ICON_RESUME : ICON_PAUSE);
        if (getModel().isRollover()) {
            setIcon(activeIcon);
        } else {
            setIcon(passiveIcon);
        }
    }

    @Override
    public void setHint(String text) {
        setToolTipText(text);
    }

    @Override
    public void click() {
        doClick();
    }

}
