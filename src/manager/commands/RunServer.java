package manager.commands;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
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
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
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


public class RunServer extends EntityCommand {
    
    private static final ITaskExecutorService   TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunServer() {
        super(
                "server", 
                Language.get("RunTX", "server@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/server.png"), 28, 28), 
                Language.get("RunTX", "server@title"), 
                (entity) -> {
                    return ((Environment) entity).canStartServer();
                }
        );
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        BinarySource source = (BinarySource) entity.model.getValue("binaries");
        if (source instanceof Release) {
            Thread checker = new Thread(() -> {
                try {
                    source.getLock().acquire();
                } catch (InterruptedException e) {}
                
                Release release = (Release) source;
                String  topLayer = entity.model.getValue("layerURI").toString();
                
                String rootUrl = release.getRemotePath();
                ISVNAuthenticationManager authMgr = ((Repository) release.model.getOwner()).getAuthManager();
                boolean online = false;
                try {
                    if (SVN.checkConnection(rootUrl, authMgr)) {
                        online = true;
                    }
                } catch (SVNException e) {
                    SVNErrorCode code = e.getErrorMessage().getErrorCode();
                    if (code != SVNErrorCode.RA_SVN_IO_ERROR && code != SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                        MessageBox.show(MessageType.ERROR, 
                                MessageFormat.format(
                                        Language.get(Repository.class.getSimpleName(), "error@message"),
                                        ((Release) source).model.getOwner().model.getPID(),
                                        e.getMessage()
                                )
                        );
                        source.getLock().release();
                    }
                }
                Map<String, Path> requiredLayers = release.getRequiredLayers(topLayer, online);
                String lostLayer = requiredLayers.entrySet().stream().filter((entry) -> {
                    return entry.getValue() == null;
                }).map((entry) -> {
                    return entry.getKey();
                }).findFirst().orElse(null);
                if (lostLayer != null) {
                    MessageBox.show(MessageType.WARNING, 
                            MessageFormat.format(Language.get("RunTX", "error@layer"), lostLayer)
                    );
                    source.getLock().release();
                }
                boolean checkResult = requiredLayers.keySet().parallelStream().allMatch((layerName) -> {
                    return Release.checkStructure(release.getLocalPath()+File.separator+layerName+File.separator+"directory.xml");
                });
                if (!checkResult) {
                    if (!online) {
                        MessageBox.show(MessageType.WARNING, Language.get("RunTX", "error@structure"));
                        source.getLock().release();
                    } else {
                        TES.executeTask(
                        release.new LoadCache(new LinkedList<>(requiredLayers.keySet())) {
                            
                            @Override
                            public Void execute() throws Exception {
                                try {
                                    return super.execute();
                                } finally {
                                    source.getLock().release();
                                }
                            }
                            
                            @Override
                            public void finished(Void t) {
                                super.finished(t);
                                if (!isCancelled()) {
                                    SwingUtilities.invokeLater(() -> {
                                        TES.enqueueTask(new RunServerTask((Environment) entity));
                                    });
                                }
                            }
                        }
                    );
                    }
                } else {
                    source.getLock().release();
                    TES.enqueueTask(new RunServerTask((Environment) entity));
                }
            });
            checker.start();
        } else {
            TES.enqueueTask(
                new RunServerTask((Environment) entity)
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
                    ((Entity) env.model.getValue("binaries")).model.getPID(),
                    env.model.getValue("database")
            ));
            this.env = env;
        }

        @Override
        public Void execute() throws Exception {
            Database     database = (Database) env.model.getValue("database");
            BinarySource source   = (BinarySource) env.model.getValue("binaries");

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
            command.add("-showSplashScreen=Server: "+env+" ("+source.model.getPID()+")");
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
