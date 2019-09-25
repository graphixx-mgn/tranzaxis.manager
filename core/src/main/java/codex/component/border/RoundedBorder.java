package codex.component.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;
import javax.swing.border.LineBorder;

/**
 * Реализация бордюра элемента с закругленными углами.
 */
public class RoundedBorder extends AbstractBorder {
    
    private final LineBorder border;
    private final int radius;

    /**
     * Конструктор бордюра.
     * @param border Базовый "линейный" бордюр.
     * @param radius Радиус закругления углов в пикселях. 
     */
    public RoundedBorder(LineBorder border, int radius) {
        this.border = border;
        this.radius = radius;
    }
    
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g;
        Shape clip = g2d.getClip();
        g2d.setClip(0, 0, 0, 0);
        border.paintBorder(c, g2d, x, y, width, height);
        
        g2d.setClip(clip);
        g2d.setColor(border.getLineColor());
        g2d.setStroke(g2d.getStroke());
        g2d.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2d.draw(new RoundRectangle2D.Double(x, y, width-1, height-1, radius, radius));
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return (getBorderInsets(c, new Insets(radius, radius, radius, radius)));
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = radius / 2;
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
    
}
