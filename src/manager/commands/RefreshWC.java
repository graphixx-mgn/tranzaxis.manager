package manager.commands;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.GroupTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import manager.nodes.Offshoot;


public class RefreshWC extends EntityCommand {

    public RefreshWC() {
        super(
                "refresh", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/rebuild.png"), 28, 28), 
                Language.get("desc"), 
                (entity) -> {
                    return true;
                }
        );
        setParameters(
                new PropertyHolder("clean", new Bool(Boolean.FALSE), true)
        );
        setGroupId("update");
    }
    
    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(
                new GroupTask<>(
                        Language.get("title") + ": "+((Offshoot) entity).getWCPath(),
                        ((UpdateWC) entity.getCommand("update")).new UpdateTask((Offshoot) entity),
                        ((BuildWC)  entity.getCommand("build")).new BuildKernelTask((Offshoot) entity),
                        ((BuildWC)  entity.getCommand("build")).new BuildSourceTask((Offshoot) entity, map.get("clean").getValue() == Boolean.TRUE)
                )
        );
    }
    
}
