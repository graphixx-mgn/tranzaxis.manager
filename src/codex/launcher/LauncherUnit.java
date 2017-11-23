package codex.launcher;

import codex.component.layout.WrapLayout;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
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

    private final static IConfigStoreService    STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private JScrollPane  launchPanel;
    private final JPanel commandPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));

    @Override
    public JComponent createViewport() {
        launchPanel = new JScrollPane();
        launchPanel.setBorder(null);
        commandPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        List<String> PIDs = STORE.readCatalogEntries(Shortcut.class);
        PIDs.forEach((PID) -> {
            final Shortcut shortcut  = (Shortcut) Entity.newInstance(Shortcut.class, PID);
            final Entity   entityRef = (Entity) shortcut.model.getValue("entity");
            
            if (entityRef == null) {
                CommandLauncher launcher = new CommandLauncher(null, null, PID);
                commandPanel.add(launcher);
            } else {
                final Entity entity  = EAS.getEntity(entityRef.getClass(), entityRef.getPID());
                final String cmdName = (String) shortcut.model.getValue("command");
                CommandLauncher launcher = new CommandLauncher(
                        (Entity) shortcut.model.getValue("entity"), 
                        entity.getCommand(cmdName),
                        PID
                );
                commandPanel.add(launcher);
                entity.model.addModelListener(new IModelListener() {
                    @Override
                    public void modelDeleted(EntityModel model) {
                        launcher.setInvalid(true);
                    }
                });
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
