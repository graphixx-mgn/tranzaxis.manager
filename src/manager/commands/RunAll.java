package manager.commands;

import codex.command.EntityCommand;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;


public class RunAll extends EntityCommand {
    
    private final static IConfigStoreService    CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    private static final ITaskExecutorService   TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

    public RunAll() {
        super(
                "whole", 
                Language.get("RunTX", "whole@title"), 
                ImageUtils.resize(ImageUtils.getByPath("/images/start.png"), 28, 28), 
                Language.get("RunTX", "whole@title"), 
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
                            
                            List<IConfigStoreService.ForeignLink> links = CAS.findReferencedEntries(source.getClass(), source.model.getID());
                            
                            @Override
                            public Void execute() throws Exception {
                                links.forEach((link) -> {
                                    try {
                                        EAS.getEntity(Class.forName(link.entryClass), link.entryID).getLock().acquire();
                                    } catch (ClassNotFoundException | InterruptedException e) {}
                                });
                                try {
                                    return super.execute();
                                } finally {
                                    links.forEach((link) -> {
                                        try {
                                            EAS.getEntity(Class.forName(link.entryClass), link.entryID).getLock().release();
                                        } catch (ClassNotFoundException e) {}
                                    });
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
                } else {
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
