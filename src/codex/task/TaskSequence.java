package codex.task;

import java.util.Arrays;
import java.util.List;

class TaskSequence<T> extends AbstractTask<T> {
    
    private final List<ITask> sequence;

    public TaskSequence(String title, ITask... tasks) {
        super(title);
        sequence = Arrays.asList(tasks);
    }

    @Override
    public T execute() throws Exception {
        sequence.forEach((task) -> {
            task.run();
        });
        return null;
    }

    @Override
    public void finished(T result) {}
    
}
