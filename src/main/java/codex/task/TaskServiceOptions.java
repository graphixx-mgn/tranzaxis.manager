package codex.task;

import codex.service.LocalServiceOptions;
import codex.utils.ImageUtils;

public class TaskServiceOptions extends LocalServiceOptions<TaskExecutorService> {

    public TaskServiceOptions(TaskExecutorService service) {
        super(service);
        setIcon(ImageUtils.getByPath("/images/tasks.png"));
    }
}
