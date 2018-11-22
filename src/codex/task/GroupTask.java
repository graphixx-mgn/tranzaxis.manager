package codex.task;

import codex.log.Logger;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Реализация контейнера задач, выполнение которых должно происходить строго 
 * последовательно.
 * @param <T> Тип результата возвращаемого методом {@link ITask#execute()}
 */
public final class GroupTask<T> extends AbstractTask<T> {
    
    private final List<ITask> sequence;

    /**
     * Конструктор групповой задачи.
     * @param title Наименование группы задач , для показа в GUI. (cм. {@link TaskMonitor}).
     * @param tasks Список задач для последовательного исполнения.
     */
    public GroupTask(String title, ITask... tasks) {
        super(title);
        sequence = Arrays.asList(tasks);
    }

    @Override
    public final T execute() throws Exception {
        boolean aborted = false;
        for (ITask task : sequence) {
            if (aborted) {
                task.cancel(true);
            } else {
                AbstractTask current = (AbstractTask) task;
                try {
                    if (current.isPauseable()) {
                        current.checkPaused();
                    }
                    current.setStatus(Status.STARTED);
                    current.finished(current.execute());
                    if (current.getStatus() != Status.CANCELLED) {
                        current.setStatus(Status.FINISHED);
                    } else {
                        throw new CancelException();
                    }
                } catch (InterruptedException e) {
                    setStatus(current, Status.CANCELLED);
                    aborted = true;
                } catch (CancelException e) {
                    setStatus(Status.CANCELLED);
                    aborted = true;
                 } catch (ExecuteException e) {
                    current.setProgress(task.getProgress(), MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                    setStatus(current, Status.FAILED);
                    Logger.getLogger().error(e.getDescription());
                    aborted = true;
                } catch (Exception e) {
                    current.setProgress(task.getProgress(), MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                    setStatus(current, Status.FAILED);
                    Logger.getLogger().warn("Error on task execution", e);
                    aborted = true;
                }
            }
        }
        return null;
    }

    @Override
    public final void finished(T result) {}

    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new GroupTaskView(getTitle(), this, sequence, (task) -> {
            sequence.forEach((subTask) -> {
                if (!subTask.getStatus().isFinal()) {
                    subTask.cancel(true);
                }
            });
            if (cancelAction != null) {
                cancelAction.accept(task);
            }
        });
    }
    
    /**
     * Одновренное выставление статуса головной и дочерней задаче.
     */
    void setStatus(AbstractTask child, Status status) {
        setStatus(status);
        child.setStatus(status);
    }
    
}
