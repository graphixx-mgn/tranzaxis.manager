package manager.commands.environment;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Offshoot;
import manager.nodes.Release;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;

public class RunServer extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public RunServer() {
        super(
                "server", 
                Language.get(Environment.class, "server@command"),
                ImageUtils.getByPath("/images/server.png"),
                Language.get(Environment.class, "server@command"),
                Environment::canStartServer
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
        environment.setVersion(environment.getLayerVersion());
        BinarySource source = environment.getBinaries();
        if (source instanceof Release) {
            TES.executeTask(new CheckCache(
                    environment,
                    new RunServerTask(environment)

            ));
        } else {
            if (((Offshoot) source).getBuiltStatus() == null && !MessageBox.confirmation(
                    MessageType.WARNING.getIcon(),
                    MessageType.WARNING.toString(),
                    Language.get(Offshoot.class, "warn@uncompiled")
            )) return;
            TES.enqueueTask(
                new RunServerTask(environment)
            );
        }
    }
    
    class RunServerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        RunServerTask(Environment env) {
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
