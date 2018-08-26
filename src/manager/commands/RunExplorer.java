package manager.commands;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.model.Entity;
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import manager.nodes.Database;
import manager.nodes.Environment;
import manager.nodes.Offshoot;


public class RunExplorer extends EntityCommand {

    public RunExplorer() {
        super(
                "explorer", 
                Language.get("RunTX", "explorer@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/explorer.png"), 28, 28), 
                Language.get("RunTX", "explorer@title"), 
                (entity) -> {
                    return entity.model.isValid();
                }
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(
                new RunExplorerTask((Environment) entity)
        );
    }
    
    class RunExplorerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        public RunExplorerTask(Environment env) {
            super(MessageFormat.format(
                    Language.get("RunTX", "explorer@task"),
                    env, 
                    ((Entity) env.model.getValue("offshoot")).model.getPID()
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            Offshoot offshoot = (Offshoot) env.model.getValue("offshoot");
            Database database = (Database) env.model.getValue("database");

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            command.addAll((List<String>) env.model.getValue("jvmServer"));
            command.add("-jar");

            StringJoiner starterPath = new StringJoiner(File.separator)
                    .add(offshoot.getWCPath())
                    .add("org.radixware")
                    .add("kernel")
                    .add("starter")
                    .add("bin")
                    .add("dist")
                    .add("starter.jar");
            command.add(starterPath.toString());

            // Starter arguments
            command.add("-authUser="+offshoot.model.getOwner().model.getValue("svnUser"));
            command.add("-workDir="+offshoot.getWCPath());
            command.add("-topLayerUri="+database.model.getValue("layerURI"));
            command.add("-disableHardlinks");
            command.add("-showSplashScreen=Server: "+env);
            command.add("org.radixware.kernel.explorer.Explorer");

            // Explorer arguments
            command.add("-language=en");
            command.add("-development");

            Logger.getLogger().debug("Start explorer command:\n{0}", String.join(" ", command));

            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            File logDir = new File(offshoot.getWCPath()+File.separator+"logs");
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
