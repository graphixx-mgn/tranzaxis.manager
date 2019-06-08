package codex.task;

import codex.service.CommonServiceOptions;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class TaskServiceOptions extends CommonServiceOptions {

    public TaskServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/tasks.png"));
    }
}
