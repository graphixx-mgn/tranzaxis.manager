package manager.commands.environment;

import codex.command.EntityCommand;
import codex.log.Logger;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;


public class RunExplorer extends EntityCommand<Environment> {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunExplorer() {
        super(
                "explorer", 
                Language.get(Environment.class, "explorer@command"),
                ImageUtils.resize(ImageUtils.getByPath("/images/explorer.png"), 28, 28), 
                Language.get(Environment.class, "explorer@command"),
                Environment::canStartExplorer
        );
    }

    @Override
    public void execute(Environment environment, Map<String, IComplexType> map) {
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

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            command.addAll(env.getJvmExplorer());
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
            command.add("\n -showSplashScreen=Server: "+env);
            command.add("\n -disableHardlinks");
            command.add("\norg.radixware.kernel.explorer.Explorer");

            // Explorer arguments
            command.add("\n -language=en");
            command.add("\n -development");

            Logger.getLogger().debug("Start explorer command:\n{0}", String.join(" ", command));

            final ProcessBuilder builder = new ProcessBuilder(
                command.stream().map(String::trim).collect(Collectors.toList())
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
