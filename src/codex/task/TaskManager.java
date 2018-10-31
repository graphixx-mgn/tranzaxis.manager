package codex.task;

import codex.log.Logger;
import codex.notification.NotificationService;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import javax.swing.AbstractAction;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JFrame;
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
                                    .filter(queued -> !queued.getStatus().isFinal())
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
    ) {
        @Override
        public void setLocationRelativeTo(Component c) {
            super.setLocationRelativeTo(SwingUtilities.getWindowAncestor(getViewport()));
        }
    
    };
    
    /**
     * Конструктор.
     */
    public TaskManager() {
        Logger.getLogger().debug("Initialize unit: Task Manager");
        ServiceRegistry.getInstance().registerService(new TaskExecutorService());
    }

    @Override
    public JComponent createViewport() {
        taskPanel = new TaskStatusBar(Arrays.asList(queuedThreadPool, demandThreadPool));
        return taskPanel;
    }

    @Override
    public void viewportBound() {
        if (SystemTray.isSupported()) {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(taskPanel);
            final TrayIcon trayIcon = new TrayIcon(frame.getIconImage(), frame.getTitle());
            trayIcon.setImageAutoSize(true);

            NotificationService notifier = new NotificationService(trayIcon);
            notifier.setCondition(() -> {
                int state = frame.getState();
                Window wnd = FocusManager.getCurrentManager().getActiveWindow();
                return state == JFrame.ICONIFIED || state == 7 || wnd == null || !wnd.isActive();
            });
            ServiceRegistry.getInstance().registerService(notifier);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                Logger.getLogger().warn("Unable to minimize window to tray: {0}", e.getMessage());
            }
        }
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
    }
    
    /**
     * Сервис, принимающий задачи на исполнение.
     */
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
