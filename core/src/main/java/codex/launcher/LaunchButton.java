package codex.launcher;

import codex.component.border.RoundedBorder;
import codex.editor.IEditor;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.MessageFormat;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

/**
 * Абстрактная кнопка-ярлык быстрого доступа к командам.
 */
abstract class LaunchButton extends JButton {
    
    public static final Border NORMAL_BORDER   = new RoundedBorder(new LineBorder(Color.decode("#CCCCCC"), 1), 18);
    public static final Border HOVER_BORDER    = new RoundedBorder((LineBorder) IEditor.BORDER_ACTIVE, 18);
    public static final Border ERROR_BORDER    = new RoundedBorder((LineBorder) IEditor.BORDER_ERROR,  18);
    public static final Border INACTIVE_BORDER = new RoundedBorder(new LineBorder(Color.decode("#999999"), 1), 18);
    
    private float opacity = 1;
    
    LaunchButton(Icon icon) {
        this(null, icon);
    }
    
    LaunchButton(String text, Icon icon) {
        super(MessageFormat.format("<html><center>{0}</center></html>", text), icon);
        
        setFont(new Font(IEditor.FONT_VALUE.getName(), Font.PLAIN, 10));
        setVerticalAlignment(text != null && !text.isEmpty() ? JLabel.TOP : JLabel.CENTER);
        setPreferredSize(new Dimension(110, 90));
        setHorizontalTextPosition(JLabel.CENTER);
        setVerticalTextPosition(JLabel.BOTTOM);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(NORMAL_BORDER);
        
        addMouseListener(new MouseAdapter() {
            
            @Override
            public void mouseEntered(MouseEvent event) {
                if (event.getModifiers() != MouseEvent.BUTTON1_MASK) {
                    getModel().setRollover(true);
                    stateChanged();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent event) {
                getModel().setRollover(false);
                stateChanged();
            }
        });
    }
    
    final void setOpacity(float opacity) {
        this.opacity = opacity;
        repaint();
    }    

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        super.paint(g2);
    }

    
    protected void stateChanged() {
        setBorder(getModel().isRollover() ? HOVER_BORDER : NORMAL_BORDER);
    }
    
    @Override
    public final void setFont(Font font) {
        super.setFont(font);
    }
    
    @Override
    public final void setVerticalAlignment(int alignment) {
        super.setVerticalAlignment(alignment);
    }
    
    @Override
    public final void setPreferredSize(Dimension dim) {
        super.setPreferredSize(dim);
    }
    
    @Override
    public final void setHorizontalTextPosition(int position) {
        super.setHorizontalTextPosition(position);
    }
    
    @Override
    public final void setVerticalTextPosition(int position) {
        super.setVerticalTextPosition(position);
    }
    
    @Override
    public final void setContentAreaFilled(boolean value) {
        super.setContentAreaFilled(value);
    }
    
    @Override
    public final void setFocusPainted(boolean value) {
        super.setFocusPainted(value);
    }
    
    @Override
    public final void setOpaque(boolean value) {
        super.setOpaque(value);
    }
    
    @Override
    public final void setBorder(Border border) {
        super.setBorder(border);
    }
    
    @Override
    public final void addMouseListener(MouseListener listener) {
        super.addMouseListener(listener);
    }
}
