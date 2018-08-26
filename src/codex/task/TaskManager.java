package codex.task;

import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private final ExecutorService queuedThreadPool = new TaskExecutor(ThreadPoolKind.Queued);
    private final ExecutorService demandThreadPool = new TaskExecutor(ThreadPoolKind.Demand);
    
    private TaskStatusBar    taskPanel;
    private final TaskDialog taskDialog = new TaskDialog(
            SwingUtilities.getWindowAncestor(getViewport()),
            new AbstractAction() {
                
                @Override
                public void actionPerformed(ActionEvent event) {
                    long running = taskDialog.taskRegistry.keySet().stream()
                                    .filter(queued -> queued.getStatus() == Status.PENDING   || queued.getStatus() == Status.STARTED)
                                    .count();
                    if (event.getID() == TaskDialog.CANCEL || running == 0) {
                        taskDialog.taskRegistry.keySet().stream().forEach((task) -> {
                            task.cancel(true);
                        });
                    } else if (event.getID() == TaskDialog.ENQUEUE || running != 0) {
                        taskDialog.taskRegistry.keySet().stream().forEach((task) -> {
                            taskPanel.addTask(task);
                        });
                    }
                    taskDialog.clear();
                }
            }
    );
    
    public TaskManager() {
        Logger.getLogger().debug("Initialize unit: Task Manager");
        ServiceRegistry.getInstance().registerService(new TaskExecutorService());
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
                ((AbstractTask) task).fireStatusChange();
            }
        });
    }
    
    /**
     * Незамедлительное исполнение задачи и регистрация в модальном диалоге.
     * При закрытии диалога, все задачи перемещаются в очередь.
     */
    void execute(ITask task, boolean quiet) {
        if (!quiet) {
            taskDialog.addTask(task);
        }
        demandThreadPool.submit(() -> {
            final Thread thread = Thread.currentThread();
            final String name   = thread.getName();
            thread.setName(name.replace(NamingThreadFactory.IDLE, "Task '"+task.getTitle()+"'"));
            try {
                task.run();
            } finally {
                thread.setName(name);
            }
        });
        if (!quiet) {
            SwingUtilities.invokeLater(() -> {
                taskDialog.setVisible(true);
            });
        }
    }
    
    public class TaskExecutorService implements ITaskExecutorService {
        
        TaskExecutorService() {}
        
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
        };
    }

}
