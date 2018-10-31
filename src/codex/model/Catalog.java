package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.task.TaskManager;
import codex.type.EntityRef;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Collection;
import javax.swing.ImageIcon;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {
    
    private final static IConfigStoreService  CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static ITaskExecutorService TES = (ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class);
    
    public Catalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }
    
    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent == null) return;
        
        Collection<String> childrenPIDs;
        if (getChildClass() != null && !(childrenPIDs = getChildrenPIDs()).isEmpty()) {
            ITask task = new LoadChildren(childrenPIDs);
            task.addListener(new ITaskListener() {
                
                private int previousMode;
                
                @Override
                public void statusChanged(ITask task, Status status) {
                    if (!status.isFinal()) {
                        try {
                            if (!islocked()) {
                                previousMode = getMode();
                                setMode(MODE_NONE);
                                getLock().acquire();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        getLock().release();
                        setMode(previousMode);
                    }
                }
            });
            TES.quietTask(task);
        }
    }
    
    @Override
    public abstract Class getChildClass();
    
    protected Collection<String> getChildrenPIDs() {
        EntityRef owner = Entity.findOwner(this);
        Integer ownerId = owner == null ? null : owner.getId();
        return CAS.readCatalogEntries(ownerId, getChildClass()).values();
    }
    
    private class LoadChildren extends AbstractTask<Void> {
        
        private final Collection<String> childrenPIDs;

        public LoadChildren(Collection<String> childrenPIDs) {
            super(MessageFormat.format(
                    Language.get(Catalog.class.getSimpleName(), "task@load"),
                    Catalog.this.getPathString()
            ));
            this.childrenPIDs = childrenPIDs;
        }

        @Override
        public Void execute() throws Exception {
            EntityRef ownerRef = Entity.findOwner(Catalog.this);
            childrenPIDs.forEach((PID) -> {
                Entity instance = Entity.newInstance(getChildClass(), ownerRef, PID);
                insert(instance);
            });
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }

}
