package codex.launcher;

import codex.component.layout.WrapLayout;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.unit.AbstractUnit;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/**
 * Панель быстрого запуска, отображается до выбора элементов дерева проводника.
 */
public final class LauncherUnit extends AbstractUnit {

    private final static IConfigStoreService  CSS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static ITaskExecutorService TES = (ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class);
    
    private JScrollPane  launchPanel;
    private final JPanel commandPanel = new JPanel(new WrapLayout(FlowLayout.LEFT));

    @Override
    public JComponent createViewport() {
        launchPanel = new JScrollPane();
        launchPanel.setBorder(null);
        commandPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
          
        return launchPanel;
    }
    
    @Override
    public void viewportBound() {
        launchPanel.setViewportView(commandPanel);
        TES.quietTask(new LoadShortcuts());
    }
    
    private class LoadShortcuts extends AbstractTask<Void> {

        public LoadShortcuts() {
            super("Load shortcuts");
        }

        @Override
        public Void execute() throws Exception {
            CSS.readCatalogEntries(null, Shortcut.class).forEach((ID, PID) -> {
                commandPanel.add(new LaunchShortcut((Shortcut) Entity.newInstance(Shortcut.class, null, PID)));
            });
            return null;
        }

        @Override
        public void finished(Void result) {
            commandPanel.add(new CreateShortcut());
        }
    
    }
    
}
