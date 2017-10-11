package codex.task;

import codex.log.Logger;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractTask<T> implements ITask<T> {
 
    private final String title;
    private Status  status;
    private Integer percent = 0;
    private String  description;
    private final FutureTask<T> future;
    private List<ITaskListener> listeners = new LinkedList<>();

    public AbstractTask(final String title) {
        future = new FutureTask<T>((Callable<T>) () -> {
            setStatus(Status.STARTED);
            try {
                finished(execute());
            } catch (InterruptedException e) {
                setStatus(Status.CANCELLED);
            } catch (Throwable e) {
                setProgress(percent, MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                setStatus(Status.FAILED);
                Logger.getLogger().error("Error on task execution", e);
            }
            return null;
        }) {       
            @Override
            protected void done() {
                if (status != Status.FAILED) {
                    setStatus(isCancelled() ? Status.CANCELLED : Status.FINISHED);
                }
            }
        };
        this.status = Status.PENDING;
        this.title = title;
    }

    @Override
    public abstract T execute() throws Exception;

    @Override
    public abstract void finished(T result);

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public final boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public final boolean isDone() {
        return future.isDone();
    }

    @Override
    public final T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public final T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public final String getTitle() {
        return title;
    }
    
    @Override
    public String getDescription() {
        if ((getStatus() == Status.STARTED && description != null) || getStatus() == Status.FAILED) {
            return description;
        } else {
            return getStatus().getDescription();
        }
    }

    public final void setProgress(int percent, String description) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Progress value should be from 0 to 100");
        }
        this.percent = percent;
        this.description = description;
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.progressChanged(this, percent, description);
        });
    }
    
    @Override
    public Integer getProgress() {
        return percent;
    }

    private void setStatus(Status state) {
        Logger.getLogger().debug("Task ''{0}'' state changed: {1} -> {2}", getTitle(), this.status, state);
        this.status = state;
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.statusChanged(this, status);
        });
    }
    
    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public final void run() {
        future.run();
    }

    @Override
    public final void addListener(ITaskListener listener) {
        listeners.add(listener);
    };
    
}
