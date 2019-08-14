package codex.task;

import codex.service.LocalServiceOptions;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class TaskServiceOptions extends LocalServiceOptions<TaskExecutorService> {

    public TaskServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/tasks.png"));
    }
}
