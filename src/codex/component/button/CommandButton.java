package codex.component.button;

import static codex.component.button.IButton.PRESS_COLOR;
import java.awt.Color;
import javax.swing.ImageIcon;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class CommandButton extends PushButton implements IButton {
    
    private static final Border NORMAL_BORDER = new LineBorder(Color.LIGHT_GRAY, 1);
    
    public CommandButton(ImageIcon icon) {
        super(icon, null);
        button.setBorder(new EmptyBorder(2, 2, 2, 2));
        setBorder(NORMAL_BORDER);
    }
    
    @Override
    protected final void redraw() {
        if (button.getModel().isPressed()) {
            setBorder(PRESS_BORDER);
            setBackground(PRESS_COLOR);
        } else if (button.getModel().isRollover()) {
            setBorder(HOVER_BORDER);
            setBackground(HOVER_COLOR);
        } else {
            setBorder(NORMAL_BORDER);
            setBackground(null);
        }
    }
    
}
