package codex.task;

import codex.component.dialog.Dialog;
import codex.unit.AbstractUnit;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private final static int QUEUE_SIZE = 5;
    
    private final ExecutorService queuedThreadPool = Executors.newFixedThreadPool(
            QUEUE_SIZE, new NamingThreadFactory(ThreadPoolKind.Queued)
    );
    private final ExecutorService demandThreadPool = Executors.newCachedThreadPool(
            new NamingThreadFactory(ThreadPoolKind.Demand)
    );
    
    private TaskStatusBar    taskPanel;
    private final TaskDialog taskDialog = new TaskDialog(
            SwingUtilities.getWindowAncestor(getViewport()),
            new AbstractAction() {
                
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (event.getID() == Dialog.EXIT || event.getID() == TaskDialog.ENQUEUE) {
                        taskDialog.taskRegistry.keySet().stream().forEach((task) -> {
                            taskPanel.addTask(task);
                        });
                        taskDialog.clear();
                    } else if (event.getID() == TaskDialog.ABORT) {
                        taskDialog.taskRegistry.keySet().stream().forEach((task) -> {
                            task.cancel(true);
                        });
                        taskDialog.clear();
                    }
                }
            }
    );

    @Override
    public JComponent createViewport() {
        taskPanel = new TaskStatusBar();
        return taskPanel;
    }
    
    /**
     * Добавление задачи в очередь на исполнение и регистрация в окне просмотра
     * задач.
     */
    public void enqueue(ITask task) {
        taskPanel.addTask(task);
        queuedThreadPool.submit(() -> {
            final Thread thread = Thread.currentThread();
            final String name   = thread.getName();
            thread.setName(name.replace("<idle>", "Task '"+task.getTitle()+"'"));
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
    public void execute(ITask task) {
        taskDialog.addTask(task);
        Future f = demandThreadPool.submit(() -> {
            final Thread thread = Thread.currentThread();
            final String name   = thread.getName();
            thread.setName(name.replace("<idle>", "Task '"+task.getTitle()+"'"));
            try {
                task.run();
            } finally {
                thread.setName(name);
            }
        });
        SwingUtilities.invokeLater(() -> {
            taskDialog.setVisible(true);
        });
    }

}
