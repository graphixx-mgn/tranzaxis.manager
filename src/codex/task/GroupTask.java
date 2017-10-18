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
                current.setStatus(Status.STARTED);
                try {
                    current.finished(current.execute());
                    current.setStatus(Status.FINISHED);
                } catch (InterruptedException e) {
                    current.setStatus(Status.CANCELLED);
                    aborted = true;
                } catch (Throwable e) {
                    current.setProgress(task.getProgress(), MessageFormat.format(Status.FAILED.getDescription(), e.getLocalizedMessage()));
                    current.setStatus(Status.FAILED);
                    Logger.getLogger().error("Error on task execution", e);
                    aborted = true;
                }
            }
        }
        if (aborted) {
            this.setStatus(Status.FAILED);
        }
        return null;
    }

    @Override
    public final void finished(T result) {}

    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new GroupTaskView(getTitle(), this, sequence, cancelAction);
    }
    
}
