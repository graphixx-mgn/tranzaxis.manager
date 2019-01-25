package manager.commands.environment;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import manager.nodes.BinarySource;
import manager.nodes.Database;
import manager.nodes.Environment;
import manager.nodes.Release;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


public class RunServer extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunServer() {
        super(
                "server", 
                Language.get("RunTX", "server@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/server.png"), 28, 28), 
                Language.get("RunTX", "server@title"),
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
                    Language.get("RunTX", "server@task"),
                    env, 
                    env.getBinaries().getPID(),
                    env.getDataBase(false)
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {      
            Database     database = env.getDataBase(false);
            BinarySource source   = env.getBinaries();

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            command.addAll(env.getJvmServer());
            command.add("-jar");

            StringJoiner starterPath = new StringJoiner(File.separator);
            starterPath.add(source.getLocalPath());
            starterPath.add("org.radixware");
            starterPath.add("kernel");
            starterPath.add("starter");
            starterPath.add("bin");
            starterPath.add("dist");
            starterPath.add("starter.jar");
            command.add(starterPath.toString());

            // Starter arguments
            command.add("\n -workDir="+source.getLocalPath());
            command.add("\n -topLayerUri="+env.getLayerUri(false));
            command.add("\n -showSplashScreen=Server: "+env+" ("+source.getPID()+")");
            command.add("\n -disableHardlinks");
            command.add("\norg.radixware.kernel.server.Server");

            // Server arguments
            command.add("\n -dbUrl");
            command.add("jdbc:oracle:thin:@"+database.getDatabaseUrl(false));
            command.add("\n -user    ");
            command.add(database.getDatabaseUser(false));
            command.add("\n -pwd     ");
            command.add(database.getDatabasePassword(false));
            command.add("\n -dbSchema");
            command.add(database.getDatabaseUser(false));
            command.add("\n -instance");
            command.add(env.getInstanceId().toString());
            command.add("\n -switchEasVerChecksOff");
            command.add("\n -useLocalJobExecutor");
            command.add("\n -ignoreDdsWarnings");
            command.add("\n -development");
            command.add("\n -autostart");

            Logger.getLogger().debug("Start server command:\n{0}", String.join(" ", command));

            final ProcessBuilder builder = new ProcessBuilder(
                    command.stream().map((item) -> {
                        return item.trim();
                    }).collect(Collectors.toList())
            );
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
