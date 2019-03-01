package manager.commands.environment;

import codex.command.EntityCommand;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;


public class RunAll extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunAll() {
        super(
                "whole", 
                Language.get(Environment.class, "whole@command"),
                ImageUtils.resize(ImageUtils.getByPath("/images/start.png"), 28, 28), 
                Language.get(Environment.class, "whole@command"),
                (environment) -> environment.canStartServer() && environment.canStartExplorer()
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
        BinarySource source = environment.getBinaries();
        if (source instanceof Release) {
            TES.executeTask(new CheckCache(
                    environment,
                    ((RunServer)   environment.getCommand("server")).new RunServerTask(environment),
                    ((RunExplorer) environment.getCommand("explorer")).new RunExplorerTask(environment)
            ));
        } else {
            TES.enqueueTask(((RunServer)   environment.getCommand("server")).new RunServerTask(environment));
            TES.enqueueTask(((RunExplorer) environment.getCommand("explorer")).new RunExplorerTask(environment));
        }
    }
    
}
