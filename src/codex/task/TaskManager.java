package codex.task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskManager {
    
    private static final TaskManager INSTANCE = new TaskManager();
    
//    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private TaskManager() {}
    
    public static TaskManager getInstance() {
        return INSTANCE;
    }
    
    public void execute(ITask task) {
        executor.submit(task);
    }
    
}
