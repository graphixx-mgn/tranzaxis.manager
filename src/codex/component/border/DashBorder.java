package codex.component.border;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.LineBorder;

/**
 * Реализация точечного бордюра элемента.
 */
public class DashBorder extends LineBorder {
    
    private final int   length;
    
    /**
     * Конструктор бордюра.
     * @param color Цвет линии.
     * @param lenght Ширина штрихов и пространства между ними.
     * @param thickness Толщина линии в пикселах.
     */
    public DashBorder(Color color, int lenght, int thickness) {
        super(color, thickness);
        this.length = lenght;
    }
    
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(getLineColor());
        g2d.setStroke(new BasicStroke(getThickness(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{length}, 0));
        g2d.draw(new RoundRectangle2D.Double(x, y, width-1, height-1, 0, 0));
    }
    
    @Override
    public boolean isBorderOpaque() {
        return false;
    }
    
}
