package manager.commands.environment;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Offshoot;
import manager.nodes.Release;

public class RunAll extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public RunAll() {
        super(
                "whole", 
                Language.get(Environment.class, "whole@command"),
                ImageUtils.getByPath("/images/start.png"),
                Language.get(Environment.class, "whole@command"),
                (environment) -> environment.canStartServer() && environment.canStartExplorer()
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
        environment.setVersion(environment.getLayerVersion());
        BinarySource source = environment.getBinaries();
        if (source instanceof Release) {
            TES.executeTask(new CheckCache(
                    environment,
                    environment.getCommand(RunServer.class).new RunServerTask(environment),
                    environment.getCommand(RunExplorer.class).new RunExplorerTask(environment)
            ));
        } else {
            if (((Offshoot) source).getBuiltStatus() == null && !MessageBox.confirmation(
                    MessageType.WARNING.getIcon(),
                    MessageType.WARNING.toString(),
                    Language.get(Offshoot.class, "warn@uncompiled")
            )) return;
            TES.enqueueTask(environment.getCommand(RunServer.class).new RunServerTask(environment));
            TES.enqueueTask(environment.getCommand(RunExplorer.class).new RunExplorerTask(environment));
        }
    }
    
}
