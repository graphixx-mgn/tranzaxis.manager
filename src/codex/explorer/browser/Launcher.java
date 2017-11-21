package codex.explorer.browser;

import codex.component.layout.WrapLayout;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Панель быстрого запуска, отображается до выбора элементов дерева проводника.
 */
final class Launcher extends JPanel {

    private final JPanel commandLaunchPanel;

    public Launcher() {
        super(new BorderLayout());
        
        commandLaunchPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
        commandLaunchPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        add(commandLaunchPanel, BorderLayout.CENTER);

        // TODO: read from DB and check entity existance
        commandLaunchPanel.add(new CreateLauncher());
    }
    
}
