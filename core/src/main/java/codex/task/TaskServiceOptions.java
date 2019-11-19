package codex.task;

import codex.model.EntityDefinition;
import codex.service.Service;
import codex.type.EntityRef;

@EntityDefinition(icon = "/images/tasks.png")
public class TaskServiceOptions extends Service<TaskExecutorService> {
    public TaskServiceOptions(EntityRef owner, String title) {
        super(owner, title);
    }
}