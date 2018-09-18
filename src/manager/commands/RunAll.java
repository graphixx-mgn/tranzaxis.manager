package manager.commands;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.Map;
import javax.swing.SwingUtilities;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;
import manager.nodes.Repository;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


public class RunAll extends EntityCommand {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunAll() {
        super(
                "whole", 
                Language.get("RunTX", "whole@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/start.png"), 28, 28), 
                Language.get("RunTX", "whole@title"), 
                (entity) -> {
                    return ((Environment) entity).canStartServer() && ((Environment) entity).canStartExplorer();
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
                                        TES.enqueueTask(((RunServer) entity.getCommand("server")).new RunServerTask((Environment) entity));
                                        TES.enqueueTask(((RunExplorer) entity.getCommand("explorer")).new RunExplorerTask((Environment) entity));
                                    });
                                }
                            }
                        }
                    );
                    }
                } else {
                    source.getLock().release();
                    TES.enqueueTask(((RunServer) entity.getCommand("server")).new RunServerTask((Environment) entity));
                    TES.enqueueTask(((RunExplorer) entity.getCommand("explorer")).new RunExplorerTask((Environment) entity));
                }
            });
            checker.start();
        } else {
            TES.enqueueTask(((RunServer) entity.getCommand("server")).new RunServerTask((Environment) entity));
            TES.enqueueTask(((RunExplorer) entity.getCommand("explorer")).new RunExplorerTask((Environment) entity));
        }
    }
    
}
