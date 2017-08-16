package codex.update;

import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import java.awt.Insets;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;

public class UpdateUnit extends AbstractUnit {
    
    private final static ImageIcon icon = ImageUtils.resize(ImageUtils.getByPath("/images/upgrade.png"), 17, 17);

    @Override
    public JComponent createViewport() {        
        JButton button = new JButton("Check upgrade");
        button.setIcon(icon);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }
    
}
