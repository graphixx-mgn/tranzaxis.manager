package manager.commands;

import codex.command.EntityCommand;
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
import manager.nodes.BinarySource;
import manager.nodes.Environment;
import manager.nodes.Release;


public class RunAll extends EntityCommand {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));

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
                            @Override
                            public Void execute() throws Exception {
                                entity.getLock().acquire();
                                return super.execute();
                            }

                            @Override
                            public void finished(Void t) {
                                super.finished(t);
                                entity.getLock().release();
                                TES.enqueueTask(((RunServer) entity.getCommand("server")).new RunServerTask((Environment) entity));
                                TES.enqueueTask(((RunExplorer) entity.getCommand("explorer")).new RunExplorerTask((Environment) entity));
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
