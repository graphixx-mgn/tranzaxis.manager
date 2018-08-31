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


public class RunServer extends EntityCommand {

    public RunServer() {
        super(
                "server", 
                Language.get("RunTX", "server@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/server.png"), 28, 28), 
                Language.get("RunTX", "server@title"), 
                (entity) -> {
                    return entity.model.isValid();
                }
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).enqueueTask(
                new RunServerTask((Environment) entity)
        );
    }
    
    class RunServerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        public RunServerTask(Environment env) {
            super(MessageFormat.format(
                    Language.get("RunTX", "server@task"),
                    env, 
                    ((Entity) env.model.getValue("offshoot")).model.getPID(),
                    env.model.getValue("database")
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            Offshoot offshoot = (Offshoot) env.model.getValue("offshoot");
            Database database = (Database) env.model.getValue("database");
            //boolean  useWC    = env.model.getValue("binaries") instanceof Offshoot;

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            command.addAll((List<String>) env.model.getValue("jvmServer"));
            command.add("-jar");

            StringJoiner starterPath = new StringJoiner(File.separator);
            //if (useWC) {
                starterPath.add(offshoot.getLocalPath());
                starterPath.add("org.radixware");
                starterPath.add("kernel");
                starterPath.add("starter");
                starterPath.add("bin");
                starterPath.add("dist");
                starterPath.add("starter.jar");
//            } else {
//                starterPath.add(offshoot.getWCPath());
//            }
            
            command.add(starterPath.toString());

            // Starter arguments
            command.add("-authUser="+offshoot.model.getOwner().model.getValue("svnUser"));
            command.add("-workDir="+offshoot.getLocalPath());
            command.add("-topLayerUri="+database.model.getValue("layerURI"));
            command.add("-disableHardlinks");
            command.add("-showSplashScreen=Server: "+env);
            command.add("org.radixware.kernel.server.Server");

            // Server arguments
            command.add("-dbUrl");
            command.add("jdbc:oracle:thin:@"+database.model.getValue("dbUrl").toString());
            command.add("-user");
            command.add(database.model.getValue("dbSchema").toString());
            command.add("-pwd");
            command.add(database.model.getValue("dbPass").toString());
            command.add("-dbSchema");
            command.add(database.model.getValue("dbSchema").toString());
            command.add("-instance");
            command.add(((List<String>) env.model.getValue("instanceId")).get(0));
            command.add("-development");
            command.add("-autostart");
            command.add("-switchEasVerChecksOff");
            command.add("-useLocalJobExecutor");
            command.add("-ignoreDdsWarnings");

            Logger.getLogger().debug("Start server command:\n{0}", String.join(" ", command));

            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            File logDir = new File(offshoot.getLocalPath()+File.separator+"logs");
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
