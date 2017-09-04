package codex.editor;

import java.awt.Color;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public interface IEditor {
    
    public static final Font FONT_NORMAL = new Font("Tahoma", 0, 12);
    public static final Font FONT_BOLD   = FONT_NORMAL.deriveFont(Font.BOLD);
    
    public static final Border BORDER_ERROR  = new LineBorder(Color.decode("#DE5347"), 2);
    public static final Border BORDER_ACTIVE = new LineBorder(Color.decode("#3399FF"), 2);
    public static final Border BORDER_NORMAL = new CompoundBorder(
            new EmptyBorder(1, 1, 1, 1),
            new LineBorder(Color.LIGHT_GRAY, 1)
    );
    
    public JLabel getLabel();
    public Box getEditor();
    public Box createEditor();
    public void setBorder(Border border);
    public void setValue(Object value);
    
}
