package codex.update;

import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Insets;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;

public class UpdateUnit extends AbstractUnit {
    
    private final static ImageIcon icon = ImageUtils.resize(ImageUtils.getByPath("/images/upgrade.png"), 17, 17);

    @Override
    public JComponent createViewport() {        
        JButton button = new JButton(Language.get("title"));
        button.setIcon(icon);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setMargin(new Insets(0, 5, 0, 5));
        return button;
    }
    
}
