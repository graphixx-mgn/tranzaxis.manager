package codex.component.button;

import codex.utils.ImageUtils;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class PushButton extends JPanel implements IButton, ChangeListener {
    
    protected final JButton button;
    
    public PushButton(ImageIcon icon, String title) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setBorder(IButton.EMPTY_BORDER);
        
        button = new JButton(title, icon != null ? ImageUtils.resize(icon, 26, 26) : null);
        button.setDisabledIcon(icon != null ? ImageUtils.grayscale(ImageUtils.resize(icon, 26, 26)) : null);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(2, 5, 2, 5));
        button.getModel().addChangeListener(this);
        
        add(button);
    }
    
    public void addActionListener(ActionListener listener) {
        button.addActionListener(listener);
    }

    @Override
    public void stateChanged(ChangeEvent event) {
        redraw();
    }
    
    protected void redraw() {
        if (button.getModel().isPressed()) {
            setBorder(PRESS_BORDER);
            setBackground(PRESS_COLOR);
        } else if (button.getModel().isRollover()) {
            setBorder(HOVER_BORDER);
            setBackground(HOVER_COLOR);
        } else {
            setBorder(EMPTY_BORDER);
            setBackground(null);
        }
    }
    
}
