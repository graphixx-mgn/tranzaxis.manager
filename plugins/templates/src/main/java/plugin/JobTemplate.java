package plugin;

import codex.log.Logger;
import codex.model.EntityDefinition;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.type.EntityRef;
import plugin.job.JobPlugin;
import java.util.Collection;
import java.util.Collections;

@EntityDefinition(title = "title", icon="/images/test.png")
public class JobTemplate extends JobPlugin {

    public JobTemplate(EntityRef owner, String title) {
        super(owner, title);
    }

    @Override
    protected Collection<ITask> getTasks() {
        return Collections.singleton(
                new AbstractTask(
                        getTitle()
                ) {
                    @Override
                    public Object execute() throws Exception {
                        Logger.getLogger().info("It's a job plugin template");
                        return null;
                    }

                    @Override
                    public void finished(Object result) {}
                }
        );
    }
}
