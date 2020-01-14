package codex.task;

import codex.service.ServiceRegistry;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Реализация контейнера задач, выполнение которых должно происходить строго 
 * последовательно.
 */
public final class GroupTask extends AbstractTask<List<ITask>> {
    
    private final List<ITask> sequence;
    private final boolean stopOnError;

    /**
     * Конструктор групповой задачи.
     * @param title Наименование группы задач , для показа в GUI. (cм. {@link TaskMonitor}).
     * @param tasks Список задач для последовательного исполнения.
     */
    public GroupTask(String title, ITask... tasks) {
        this(title, true, tasks);
    }

    public GroupTask(String title, boolean stopOnError, ITask... tasks) {
        super(title);
        this.sequence = Arrays.asList(tasks);
        this.stopOnError = stopOnError;
    }

    Collection<ITask> getSequence() {
        return new ArrayList<>(sequence);
    }

    boolean isStopOnError() {
        return stopOnError;
    }

    @Override
    public final List<ITask> execute() throws Exception {
        try {
            for (ITask task : sequence) {
                AbstractTask current = (AbstractTask) task;
                try {
                    if (current.isPauseable()) {
                        current.checkPaused();
                    }
                    ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(current);
                    current.get();
                } catch (CancellationException e) {
                    throw new CancelException();

                } catch (ExecutionException e) {
                    if (stopOnError) throw new ExecuteException(
                            e.getCause().getMessage(),
                            MessageFormat.format("Subtask ''{0}'' failed: {1}", current.getTitle(), e.getCause().getMessage())
                    );
                }
            }
        } finally {
            sequence.forEach(subTask -> {
                if (!subTask.getStatus().isFinal()) {
                    subTask.cancel(true);
                    ((AbstractTask) subTask).setStatus(Status.CANCELLED);
                }
            });
        }
        return sequence;
    }

    @Override
    public void finished(List<ITask> result) {}

    @Override
    public AbstractTaskView createView(Consumer<ITask> cancelAction) {
        return new GroupTaskView<>(
                getTitle(),
                this,
                sequence,
                task -> {
                    sequence.forEach((subTask) -> {
                        if (!subTask.getStatus().isFinal()) {
                            subTask.cancel(true);
                        }
                    });
                    if (cancelAction != null) {
                        cancelAction.accept(task);
                    }
                }
        );
    }
}
