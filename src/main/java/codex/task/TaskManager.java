package codex.task;

import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import javax.swing.*;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private TaskStatusBar statusBar = new TaskStatusBar();
    
    /**
     * Конструктор.
     */
    public TaskManager() {
        Logger.getLogger().debug("Initialize unit: Task Manager");
        ServiceRegistry.getInstance().addRegistryListener(ITaskExecutorService.class, service -> {
            ((ITaskExecutorService) service).getAccessor().attachMonitor(ThreadPoolKind.Queued, statusBar.getMonitor());
        });
    }

    @Override
    public JComponent createViewport() {
        return statusBar;
    }

}
