package plugin;

import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.type.EntityRef;
import plugin.job.JobPlugin;
import java.util.Collection;
import java.util.Collections;

@EntityDefinition(title = "title", icon="/images/test.png")
public class JobTemplate extends JobPlugin {

    public JobTemplate(EntityRef<Entity> owner, String title) {
        super(owner, title);
    }

    @Override
    protected Collection<ITask> getTasks() {
        return Collections.singleton(
                new AbstractTask<Void>(getTitle()) {
                    @Override
                    public Void execute() throws Exception {
                        Logger.getLogger().info("It's a job plugin template");
                        return null;
                    }
                }
        );
    }
}