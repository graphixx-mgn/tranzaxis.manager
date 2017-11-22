package codex.launcher;

import codex.component.layout.WrapLayout;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/**
 * Панель быстрого запуска, отображается до выбора элементов дерева проводника.
 */
public final class LauncherUnit extends AbstractUnit {

    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private JScrollPane  launchPanel;
    private final JPanel commandPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));

    @Override
    public JComponent createViewport() {
        launchPanel = new JScrollPane();
        launchPanel.setBorder(null);

        commandPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        List<String> PIDs = STORE.readCatalogEntries(Shortcut.class);
        PIDs.forEach((PID) -> {
            final Shortcut shortcut = (Shortcut) Entity.newInstance(Shortcut.class, PID);
            final Entity   entity = (Entity) shortcut.model.getValue("entity");
            final String   cmdName = (String) shortcut.model.getValue("command");
            
            if (entity == null) {
                CommandLauncher launcher = new CommandLauncher(null, null, PID);
                commandPanel.add(launcher);
            } else {
                CommandLauncher launcher = new CommandLauncher(
                        (Entity) shortcut.model.getValue("entity"), 
                        entity.getCommand(cmdName),
                        PID
                );
                commandPanel.add(launcher);
            }
        });
        commandPanel.add(new CreateLauncher());
        return launchPanel;
    }
    
    @Override
    public void viewportBound() {
        launchPanel.setViewportView(commandPanel);
    }
    
}
