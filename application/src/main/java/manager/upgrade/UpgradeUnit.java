package manager.upgrade;

import codex.log.Logger;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.utils.Versioning;
import manager.xml.Version;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;

public final class UpgradeUnit extends AbstractUnit {
    
    private final static ImageIcon ICON = ImageUtils.resize(ImageUtils.getByPath("/images/upgrade.png"), 17, 17);

    private final Version currentVersion;
    
    public UpgradeUnit() {
        Logger.getLogger().debug("Initialize unit: Upgrade Manager");
        currentVersion = UpgradeService.getVersion();
    }

    @Override
    public JComponent createViewport() {
        JLabel label = new JLabel(
                MessageFormat.format(Language.get("current"), currentVersion.getNumber()),
                ICON, SwingConstants.CENTER
        ) {{
            setBorder(new EmptyBorder(new Insets(2, 10, 2, 10)));
        }};
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                new Versioning.ShowChanges(UpgradeService.getHistory()).execute(null);
            }
        });
        return label;
    }
}
