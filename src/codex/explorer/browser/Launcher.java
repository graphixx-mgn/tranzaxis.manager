package codex.explorer.browser;

import codex.command.EntityCommand;
import codex.component.layout.WrapLayout;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Панель быстрого запуска, отображается до выбора элементов дерева проводника.
 */
final class Launcher extends JPanel {

    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private final JPanel commandLaunchPanel;

    Launcher() {
        super(new BorderLayout());
        
        commandLaunchPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));
        commandLaunchPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        add(commandLaunchPanel, BorderLayout.CENTER);

        List<String> PIDs = STORE.readCatalogEntries(Shortcut.class);
        PIDs.forEach((PID) -> {
            final Shortcut shortcut = (Shortcut) Entity.newInstance(Shortcut.class, PID);
            final Entity   entity = (Entity) shortcut.model.getValue("entity");
            final String   cmdName = (String) shortcut.model.getValue("command");
            for (EntityCommand command : entity.getCommands()) {
                if (command.getName().equals(cmdName)) {
                    CommandLauncher launcher = new CommandLauncher(
                            (Entity) shortcut.model.getValue("entity"), 
                            command,
                            PID
                    );
                    commandLaunchPanel.add(launcher);
                    break;
                }
            }
        });
        commandLaunchPanel.add(new CreateLauncher());
    }
    
}
