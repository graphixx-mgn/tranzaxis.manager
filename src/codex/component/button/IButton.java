package codex.component.button;

import java.awt.Color;
import java.awt.event.ActionListener;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public interface IButton {
    
    public final static Border EMPTY_BORDER = new EmptyBorder(1, 1, 1, 1);
    public final static Border HOVER_BORDER = new LineBorder(Color.decode("#C0DCF3"), 1);
    public final static Border PRESS_BORDER = new LineBorder(Color.decode("#90C8F6"), 1);
    
    public final static Color  HOVER_COLOR  = Color.decode("#D8E6F2");
    public final static Color  PRESS_COLOR  = Color.decode("#C0DCF3");
    
    public void addActionListener(ActionListener listener);
    
}
