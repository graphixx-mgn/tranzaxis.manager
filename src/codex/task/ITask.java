package codex.task;

import java.util.concurrent.Callable;

public interface ITask<T> extends Callable<T> {
    
    public enum State {
        PENDING,
        STARTED,
        FINISHED
    }
    
    T execute();
    void cancel();
    void finished(T result);
    
}
