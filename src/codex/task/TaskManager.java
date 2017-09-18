package codex.task;

import codex.log.Logger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class TaskManager {
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    public void test() {  
        Callable task = () -> {
            try {
                Logger.getLogger().info("Start task");
                TimeUnit.SECONDS.sleep(2);
                return 123;
            } catch (InterruptedException e) {
                throw new IllegalStateException("task interrupted", e);
            }
        };
        Future<Integer> res = executor.submit(task);
        
//        try {
//            Logger.getLogger().info("Result: ", res.get());
//        } catch (InterruptedException | ExecutionException ex) {
//            Logger.getLogger().error("Error", ex);
//        }
    }
    
}
