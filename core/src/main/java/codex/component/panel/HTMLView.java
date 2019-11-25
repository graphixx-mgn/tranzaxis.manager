package codex.component.panel;

import codex.utils.ImageUtils;
import javax.swing.*;
import java.awt.*;

public class HTMLView extends JEditorPane {
    // Initializer
    {
        setEditorKit(new ImageUtils.HTMLToolKit());
        setOpaque(false);
        setContentType("text/html");
        setEditable(false);
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    }

    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        super.paintComponent(g2);
    }
}
