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
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;


public class RunExplorer extends EntityCommand {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

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
        BinarySource source = (BinarySource) entity.model.getValue("binaries");
        if (source instanceof Release) {
            Thread checker = new Thread(() -> {
                Release release = (Release) source;
                String  topLayer = entity.model.getValue("layerURI").toString();
                List<String> requiredLayers = release.getRequiredLayers(topLayer);
                
                boolean result = requiredLayers.parallelStream().allMatch((layerName) -> {
                    return Release.checkStructure(release.getLocalPath()+File.separator+layerName+File.separator+"directory.xml");
                });
                if (!result) {
                    TES.executeTask(
                        release.new LoadCache(requiredLayers) {
                            @Override
                            public Void execute() throws Exception {
                                entity.getLock().acquire();
                                try {
                                    return super.execute();
                                } finally {
                                    entity.getLock().release();
                                }
                            }

                            @Override
                            public void finished(Void t) {
                                super.finished(t);
                                if (!isCancelled()) {
                                    TES.enqueueTask(new RunExplorerTask((Environment) entity));
                                }
                            }
                        }
                    );
                } else {
                    TES.enqueueTask(new RunExplorerTask((Environment) entity));
                }
            });
            checker.start();
        } else {
            TES.enqueueTask(
                new RunExplorerTask((Environment) entity)
            );
        }
    }
    
    class RunExplorerTask extends AbstractTask<Void> {

        private final Environment env;
        Process process;
        
        public RunExplorerTask(Environment env) {
            super(MessageFormat.format(
                    Language.get("RunTX", "explorer@task"),
                    env, 
                    ((Entity) env.model.getValue("binaries")).model.getPID()
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            BinarySource source = (BinarySource) env.model.getValue("binaries");

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            command.addAll((List<String>) env.model.getValue("jvmServer"));
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
            command.add("-workDir="+source.getLocalPath());
            command.add("-topLayerUri="+((List<String>) env.model.getValue("layerURI")).get(0));
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
