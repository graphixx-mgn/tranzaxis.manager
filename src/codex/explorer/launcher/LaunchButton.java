package codex.explorer.launcher;

import codex.component.border.RoundedBorder;
import codex.editor.IEditor;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.MessageFormat;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Абстрактная кнопка-ярлык быстрого доступа к командам.
 */
abstract class LaunchButton extends JButton implements ChangeListener {
    
    public static final Border NORMAL_BORDER = new RoundedBorder(new LineBorder(Color.decode("#CCCCCC"), 1), 18);
    public static final Border HOVER_BORDER  = new RoundedBorder((LineBorder) IEditor.BORDER_ACTIVE, 18);
    public static final Border ERROR_BORDER  = new RoundedBorder((LineBorder) IEditor.BORDER_ERROR,  18);
    
    private float opacity = 1;
    
    LaunchButton(Icon icon) {
        this(null, icon);
    }
    
    LaunchButton(String text, Icon icon) {
        super(MessageFormat.format("<html><center>{0}</center></html>", text), icon);
        setVerticalAlignment(text != null && !text.isEmpty() ? JLabel.TOP : JLabel.CENTER);
        setPreferredSize(new Dimension(95, 100));
        setHorizontalTextPosition(JLabel.CENTER);
        setVerticalTextPosition(JLabel.BOTTOM);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(NORMAL_BORDER);
        getModel().addChangeListener(this);
    }
    
    public final void setOpacity(float opacity) {
        this.opacity = opacity;
        repaint();
    }    

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        super.paint(g2);
    }

    @Override
    public void stateChanged(ChangeEvent event) {
        setBorder(getModel().isRollover() ? HOVER_BORDER : NORMAL_BORDER);
    }
    
}
