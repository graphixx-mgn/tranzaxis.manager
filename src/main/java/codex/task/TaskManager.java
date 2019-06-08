package codex.task;

import codex.log.Logger;
import codex.service.AbstractService;
import codex.service.CommonServiceOptions;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import javax.swing.*;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private final ExecutorService queuedThreadPool = new TaskExecutor(ThreadPoolKind.Queued);
    private final ExecutorService demandThreadPool = new TaskExecutor(ThreadPoolKind.Demand);
    
    private TaskStatusBar taskPanel;
    private TaskDialog taskDialog = new TaskDialog(
            null,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    long running = taskDialog.runningTasks();
                    if (event.getID() == TaskDialog.CANCEL || running == 0) {
                        taskDialog.taskRegistry.keySet().forEach((task) -> {
                            task.cancel(true);
                        });
                    } else if (event.getID() == TaskDialog.ENQUEUE || running != 0) {
                        taskDialog.taskRegistry.keySet().stream().forEach((task) -> {
                            taskDialog.removeTask(task);
                            taskPanel.addTask(task);
                        });
                    }
                }
            }
    ) {
        @Override
        public void setLocationRelativeTo(Component c) {
            Window owner = IComplexType.coalesce(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    SwingUtilities.getWindowAncestor(getViewport())
            );
            super.setLocationRelativeTo(owner);
        }
    };
    
    /**
     * Конструктор.
     */
    public TaskManager() {
        Logger.getLogger().debug("Initialize unit: Task Manager");
        ServiceRegistry.getInstance().registerService(new TaskExecutorService());
        getViewport();
    }

    @Override
    public JComponent createViewport() {
        taskPanel = new TaskStatusBar(Arrays.asList(queuedThreadPool, demandThreadPool));
        return taskPanel;
    }
    
    /**
     * Добавление задачи в очередь на исполнение и регистрация в окне просмотра
     * задач.
     */
    void enqueue(ITask task) {
        taskPanel.addTask(task);
        queuedThreadPool.submit(() -> {
            final Thread thread = Thread.currentThread();
            final String name   = thread.getName();
            thread.setName(name.replace(NamingThreadFactory.IDLE, "Task '"+task.getTitle()+"'"));
            try {
                task.run();
            } finally {
                thread.setName(name);
            }
        });
    }
    
    /**
     * Незамедлительное исполнение задачи и регистрация в модальном диалоге.
     * При закрытии диалога, все задачи перемещаются в очередь.
     */
    void execute(ITask task, boolean quiet) {
        Runnable runnable = () -> {
            final Thread thread = Thread.currentThread();
            final String name   = thread.getName();
            thread.setName(name.replace(NamingThreadFactory.IDLE, "Task '"+task.getTitle()+"'"));
            try {
                task.run();
            } finally {
                thread.setName(name);
            }
        };

        if (!quiet) {
            taskDialog.addTask(task);
        }
        demandThreadPool.submit(runnable);
    }
    
    /**
     * Сервис, принимающий задачи на исполнение.
     */
    public class TaskExecutorService extends AbstractService<TaskServiceOptions> implements ITaskExecutorService {
        
        @Override
        public void enqueueTask(ITask task) {
            enqueue(task);
        }

        @Override
        public void executeTask(ITask task) {
            execute(task, false);
        }
        
        @Override
        public void quietTask(ITask task) {
            execute(task, true);
        }
        
        @Override
        public boolean isStoppable() {
            return false;
        }

    }

}
