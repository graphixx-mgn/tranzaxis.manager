package codex.task;

import codex.unit.AbstractUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComponent;

/**
 * Модуль-исполнитель задач {@link ITask}.
 */
public final class TaskManager extends AbstractUnit {
    
    private final ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private       TaskStatusBar   viewPort;

    @Override
    public JComponent createViewport() {
        viewPort = new TaskStatusBar();
        return viewPort;
    }
    
    /**
     * Добавление задачи в очередь на исполнение и регистрация в окне просмотра
     * задач.
     */
    public void execute(ITask task) {
        viewPort.addTask(task);
        threadPool.submit(task);
    }
    
}
