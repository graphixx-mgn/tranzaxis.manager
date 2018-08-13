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
import java.util.function.Consumer;

/**
 * Абстрактная реализация задачи {@link ITask}, следует использовать в качестве
 * предка при разработке пользовательских задач. Содержит весь необходииый для
 * исполнения и управления состоянием код.
 * @param <T> Тип результата возвращаемого методом {@link ITask#execute()}
 */
public abstract class AbstractTask<T> implements ITask<T> {
 
    private final String title;
    private Status  status;
    private Integer percent = 0;
    private String  description;
    private final FutureTask<T> future;
    private List<ITaskListener> listeners = new LinkedList<>();

    /**
     * Конструктор задачи.
     * @param title Наименование задачи, для показа в GUI. (cм. {@link TaskMonitor}).
     */
    public AbstractTask(final String title) {
        future = new FutureTask<T>((Callable<T>) () -> {
            setStatus(Status.STARTED);
            try {
                finished(execute());
            } catch (CanceException e) {
                setStatus(Status.CANCELLED);
            } catch (InterruptedException e) {
            } catch (Throwable e) {
                setProgress(percent, MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                setStatus(Status.FAILED);
                Logger.getLogger().error("Error on task execution", e);
                throw e;
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

    /**
     * Метод отмены задачи.
     * @param mayInterruptIfRunning Форсировать отмену, даже если задаче уже 
     * выполняется, иначе - отменится задача только в статусе ожидания.
     */
    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    /**
     * Возвращает признак того что задача была отменена.
     */
    @Override
    public final boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Возвращает признак того что задача была завершена.
     */
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
    public final String getDescription() {
        if ((getStatus() == Status.STARTED && description != null) || getStatus() == Status.FAILED) {
            return description;
        } else {
            return getStatus().getDescription();
        }
    }

    /**
     * Установить прогресс задачи и описание состояния на данно этапе прогресса.
     * Следует вызывать из прикладной реализации метода {@link ITask#execute()}
     * если имеется возможность определить процент готовности.
     * @param percent Процент готовности (0-100).
     * @param description Описание состояния задачи на данный момент.
     */
    @Override
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
    public final Integer getProgress() {
        return percent;
    }

    /**
     * Установить состояние задачи.
     * @param state Константа типа {@link Status}
     */
    void setStatus(Status state) {
        if (!this.status.equals(state)) {
            Logger.getLogger().debug("Task ''{0}'' state changed: {1} -> {2}", getTitle(), this.status, state);
        }
        this.status = state;
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.statusChanged(this, status);
        });
    }
    
    @Override
    public final Status getStatus() {
        return status;
    }
    
    public final void fireStatusChange() {
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.statusChanged(this, status);
        });
    }

    /**
     * Запустить исполнение задачи. Вызывается сервисом исполнения задач в {@link TaskManager}.
     */
    @Override
    public final void run() {
        future.run();
    }
    
    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new TaskView(this, cancelAction);
    };

    @Override
    public final void addListener(ITaskListener listener) {
        listeners.add(listener);
    };
    
}
