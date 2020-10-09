package codex.component.ui;

import net.jcip.annotations.ThreadSafe;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.Objects;
import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 * Визуальное представление (UI) недетерминантного прогресс-бара. Вместо бегущего
 * прямоугольника рисуются косые полоски.
 * 
 */
@ThreadSafe
public class StripedProgressBarUI extends BasicProgressBarUI {

    public final static Color PROGRESS_NORMAL   = UIManager.getDefaults().getColor("ProgressBar.foreground");
    public final static Color PROGRESS_INFINITE = Color.decode("#A0C8FF");
    
    private final boolean forward;
    
    /**
     * Конструктор представления.
     * @param forward Направление движения. TRUE - вперед, FALSE - назад.
     */
    public StripedProgressBarUI(boolean forward) {
        super();
        this.forward = forward;
    }
    
    /**
     * Рассчет ширины бегущего прямоугольника, в данном случае нужно занимать
     * всю ширину.
     * @param availableLength Ширина прогресс-бара.
     * @param otherDimension Высота прогресс-бара.
     * @return Рассчитанная ширина.
     */
    @Override 
    protected int getBoxLength(int availableLength, int otherDimension) {
        return availableLength;
    }
    
    /**
     * Метод отрисовки недетерминантного представления.
     * @param graphics Графический контекст.
     * @param component SWING компонент (прогресс-бар).
     */
    @Override 
    public void paintIndeterminate(Graphics graphics, JComponent component) {
        Insets b = progressBar.getInsets();
        int barRectWidth  = progressBar.getWidth() - b.right - b.left;
        int barRectHeight = progressBar.getHeight() - b.top - b.bottom;

        if (barRectWidth <= 0 || barRectHeight <= 0) {
            return;
        }
        try {
            boxRect = getBox(boxRect);
            if (Objects.nonNull(boxRect)) {
                int w = 10;
                int x = getAnimationIndex();
                GeneralPath p = new GeneralPath();
                if (forward) {
                    p.moveTo(boxRect.x,           boxRect.y);
                    p.lineTo(boxRect.x + w * .5f, boxRect.y + boxRect.height);
                    p.lineTo(boxRect.x + w,       boxRect.y + boxRect.height);
                    p.lineTo(boxRect.x + w * .5f, boxRect.y);
                } else {
                    p.moveTo(boxRect.x,           boxRect.y + boxRect.height);
                    p.lineTo(boxRect.x + w * .5f, boxRect.y + boxRect.height);
                    p.lineTo(boxRect.x + w,       boxRect.y);
                    p.lineTo(boxRect.x + w * .5f, boxRect.y);
                }
                p.closePath();

                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(progressBar.getForeground());
                if (forward) {
                    for (int i = boxRect.width + x; i > -w; i -= w) {
                        g2.fill(AffineTransform.getTranslateInstance(i, 0).createTransformedShape(p));
                    }
                } else {
                    for (int i = -x; i < boxRect.width; i += w) {
                        g2.fill(AffineTransform.getTranslateInstance(i, 0).createTransformedShape(p));
                    }
                }
                if (progressBar.isStringPainted()) {
                    int amountFull = getAmountFull(b, barRectWidth, barRectHeight);
                    paintString(g2, b.left, b.top, barRectWidth, barRectHeight, amountFull, b);
                }
                g2.dispose();
            }
        } catch (Exception e) {}
    }

    @Override
    protected Color getSelectionForeground() {
        return progressBar.isIndeterminate() ? Color.decode("#3C6586"): super.getSelectionForeground();
    }
    
}
