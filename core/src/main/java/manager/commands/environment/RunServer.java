package manager.commands.environment;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.BinarySource;
import manager.nodes.Database;
import manager.nodes.Environment;
import manager.nodes.Release;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;


public class RunServer extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunServer() {
        super(
                "server", 
                Language.get(Environment.class, "server@command"),
                ImageUtils.resize(ImageUtils.getByPath("/images/server.png"), 28, 28), 
                Language.get(Environment.class, "server@command"),
                Environment::canStartServer
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
        BinarySource source = environment.getBinaries();
        if (source instanceof Release) {
            TES.executeTask(new CheckCache(
                    environment,
                    new RunServerTask(environment)

            ));
        } else {
            TES.enqueueTask(
                new RunServerTask(environment)
            );
        }
    }
    
    class RunServerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        public RunServerTask(Environment env) {
            super(MessageFormat.format(
                    Language.get(Environment.class, "server@task"),
                    env, 
                    env.getBinaries().getPID(),
                    env.getDataBase(false)
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            BinarySource source = env.getBinaries();
            final ProcessBuilder builder = new ProcessBuilder(env.getServerCommand(true));
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            File logDir = new File(source.getLocalPath()+File.separator+"logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            builder.directory(logDir);

            addListener(new ITaskListener() {
                @Override
                public void statusChanged(ITask task, Status status) {
                    if (status.equals(Status.CANCELLED)) {
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
