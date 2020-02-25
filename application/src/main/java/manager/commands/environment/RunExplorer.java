package manager.commands.environment;

import codex.command.EntityCommand;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;
import java.io.File;
import java.text.MessageFormat;
import java.util.Map;

public class RunExplorer extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public RunExplorer() {
        super(
                "explorer", 
                Language.get(Environment.class, "explorer@command"),
                ImageUtils.getByPath("/images/explorer.png"),
                Language.get(Environment.class, "explorer@command"),
                Environment::canStartExplorer
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
        environment.setVersion(environment.getLayerVersion());
        BinarySource source = environment.getBinaries();
        if (source instanceof Release) {
            TES.executeTask(new CheckCache(
                    environment,
                    new RunExplorerTask(environment)

            ));
        } else {
            TES.enqueueTask(
                new RunExplorerTask(environment)
            );
        }
    }
    
    class RunExplorerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        public RunExplorerTask(Environment env) {
            super(MessageFormat.format(
                    Language.get(Environment.class, "explorer@task"),
                    env, 
                    env.getBinaries().getPID()
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            BinarySource source = env.getBinaries();
            final ProcessBuilder builder = new ProcessBuilder(env.getExplorerCommand(true));
            final File logDir = new File(source.getLocalPath()+File.separator+"logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            builder.directory(logDir);

            addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                    if (nextStatus.equals(Status.CANCELLED)) {
                        process.destroy();
                    }
                }
            });
            process = builder.start();
            process.waitFor();
            
            return null;
        }

        @Override
        public void finished(Void t) {}
    
    }
    
}
