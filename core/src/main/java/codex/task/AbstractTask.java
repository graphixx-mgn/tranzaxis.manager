package codex.task;

import codex.log.Logger;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
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
    private final List<ITaskListener> listeners = new LinkedList<>();
    private final Semaphore     semaphore = new Semaphore(1, true) {
        @Override
        public void release() {
            if (availablePermits() == 0) {
                // Avoid extra releases that increases permits counter
                super.release();
            }
        }
    };
    private LocalDateTime startTime, pauseTime, stopTime;

    /**
     * Конструктор задачи.
     * @param title Наименование задачи, для показа в GUI. (cм. {@link TaskMonitor}).
     */
    public AbstractTask(final String title) {
        future = new FutureTask<T>(() -> {
            try {
                TaskOutput.defineContext(this);
                new LinkedList<>(listeners).forEach((listener) -> listener.beforeExecute(this));
                T result;
                setStatus(Status.STARTED);
                try {
                    result = execute();
                } finally {
                    new LinkedList<>(listeners).forEach((listener) -> listener.afterExecute(this));
                }
                setStatus(Status.FINISHED);
                finished(result);
            } catch (CancelException e) {
                if (isCancelled() && status != Status.CANCELLED) {
                    setStatus(Status.CANCELLED);
                }
            } catch (InterruptedException e) {
                //
            } catch (Throwable e) {
                setProgress(percent, MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                setStatus(Status.FAILED);
                Logger.getLogger().error(MessageFormat.format("Error on task ''{0}'' execution", getTitle()), e);
                throw e;
            } finally {
                synchronized (listeners) {
                    listeners.clear();
                }
                TaskOutput.clearContext();
                System.gc();
            }
            return null;
        }) {
            @Override
            protected void done() {
                if (isCancelled() && status != Status.CANCELLED) {
                    setStatus(Status.CANCELLED);
                } else if (!isCancelled() && !isFailed()) {
                    setStatus(Status.FINISHED);
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
        if (semaphore.availablePermits() == 0) {
            setPause(false);
        }
        return future.cancel(mayInterruptIfRunning);
    }
    
    /**
     * Возвращает признак того что задача была завершена с ошибкой.
     */
    public final boolean isFailed() {
        return status == Status.FAILED;
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
    public boolean isPauseable() {
        return false;
    }
    
    private Status prevStatus;
    final void setPause(boolean paused) {
        if (isPauseable()) {
            if (paused) {
                try {
                    prevStatus = getStatus();
                    setStatus(Status.PAUSED);
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    //
                }
            } else {
                setStatus(prevStatus);
                semaphore.release();
            }
        }
    }
    
    @Override
    public final void checkPaused() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            //
        } finally {
            semaphore.release();
        }
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
     * Возвращает общее время исполнения задачи, учитывая приостановку.
     */
    protected final long getDuration() {
        if (startTime == null) {
            return 0;
        } else {
            return Duration.between(
                    startTime,
                    status.isFinal() ? stopTime : LocalDateTime.now()
            ).toMillis();
        }
    }

    /**
     * Установить состояние задачи и вернуть предыдущее.
     * @param state Константа типа {@link Status}
     */
    void setStatus(Status state) {
        if ((this.status == Status.PENDING || this.status == Status.PAUSED) && state == Status.STARTED) {
            if (pauseTime == null) {
                startTime = LocalDateTime.now();
            } else {
                startTime = startTime.minusNanos(Duration.between(LocalDateTime.now(), pauseTime).toNanos());
            }
        } else if (this.status == Status.STARTED && state == Status.PAUSED) {
            pauseTime = LocalDateTime.now();
        }
        if (state.isFinal()) {
            stopTime = LocalDateTime.now();
        }
        Status prevStatus = this.status;
        this.status = state;
        new LinkedList<>(listeners).forEach((listener) -> listener.statusChanged(this, prevStatus, status));
    }
    
    @Override
    public final Status getStatus() {
        return status;
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
    }

    @Override
    public final void addListener(ITaskListener listener) {
        if (ITaskExecutorService.class.isAssignableFrom(listener.getClass())) {
            listeners.add(0, listener);
        } else {
            listeners.add(listener);
        }
    }

    @Override
    public final void removeListener(ITaskListener listener) {
        listeners.remove(listener);
    }

}
