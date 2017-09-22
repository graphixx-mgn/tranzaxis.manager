package codex.task;

import codex.unit.AbstractUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JComponent;

public final class TaskManager extends AbstractUnit {
    
    private final ExecutorService threadPool = Executors.newFixedThreadPool(3);
    private       TaskView        viewPort;

    @Override
    public JComponent createViewport() {
        viewPort = new TaskView();
        return viewPort;
    }
    
    public void execute(ITask task) {
        viewPort.addTask(task);
        threadPool.submit(task);
    }
    
    public void executeSequentially(ITask... tasks) {
        for (ITask task : tasks) {
            viewPort.addTask(task);
        }
        threadPool.submit(new TaskSequence("Test group task", tasks));
    }
}
