package codex.task;

import codex.log.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public abstract class AbstractTask<T> implements ITask<T> {
    
    final String  title;
    private State state;
    FutureTask<T> future;
    
    public AbstractTask(String title) {
        this.title = title;
        state  = State.PENDING;
        future = new FutureTask<T>(this) {
            
            @Override
            public void run() {
                setState(State.STARTED);
                T result = execute();
                setState(State.FINISHED);
                finished(result);
            }
        };
    }
    
    public String getTitle() {
        return title;
    }

    @Override
    public abstract T execute();

    @Override
    public abstract void finished(T result);

    
    @Override
    public T call() throws Exception {
        try {
            if (state != State.PENDING) {
                throw new IllegalStateException("Wrong task state");
            }
            future.run();
            return future.get();
        }  catch (IllegalStateException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public final void cancel() {
        future.cancel(true);
    };
    
    private void setState(State state) {
        this.state = state;
        //Logger.getLogger().debug("new state: {0}", state);
    }
    
    public final void setProgress(int percent, String description) {
        if (percent > 100) {
            throw new IllegalStateException("Wrong progress state");
        }
        Logger.getLogger().info("Progress: {0}", percent);
    }
    
}
