package codex.task;

import java.util.concurrent.RunnableFuture;

public interface ITask<T> extends RunnableFuture<T> {

    String getTitle();
    Status getStatus();
    
    T execute() throws Exception;
    void finished(T result);
    
    void addListener(ITaskListener listener);
    
}
