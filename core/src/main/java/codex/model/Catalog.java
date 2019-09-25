package codex.model;

import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import codex.task.*;
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

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

    public Catalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null && getChildClass() != null) {
            loadChildren();
        }
    }
    
    @Override
    public abstract Class<? extends Entity> getChildClass();

    public void loadChildren() {
        Collection<String> childrenPIDs = getChildrenPIDs();
        if (!childrenPIDs.isEmpty()) {
            TES.quietTask(new LoadChildren(childrenPIDs) {
                private int mode = getMode();
                {
                    addListener(new ITaskListener() {
                        @Override
                        public void beforeExecute(ITask task) {
                            try {
                                if (!islocked()) {
                                    mode = getMode();
                                    setMode(MODE_NONE);
                                    getLock().acquire();
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }

                @Override
                public void finished(Void result) {
                    getLock().release();
                    setMode(mode);
                }
            });
        }
    }
    
    protected Collection<String> getChildrenPIDs() {
        Entity owner = this.getOwner();
        Integer ownerId = owner == null ? null : owner.getID();
        final IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
        return CAS.readCatalogEntries(ownerId, getChildClass()).values();
    }
    
    private class LoadChildren extends AbstractTask<Void> {
        
        private final Collection<String> childrenPIDs;

        public LoadChildren(Collection<String> childrenPIDs) {
            super(MessageFormat.format(
                    Language.get(Catalog.class, "task@load"),
                    Catalog.this.getPathString()
            ));
            this.childrenPIDs = childrenPIDs;
        }

        @Override
        public Void execute() throws Exception {
            EntityRef ownerRef = Entity.findOwner(Catalog.this);
            childrenPIDs.forEach((PID) -> {
                Entity instance = Entity.newInstance(getChildClass(), ownerRef, PID);
                if (!childrenList().contains(instance)) {
                    insert(instance);
                }
            });
            return null;
        }

        @Override
        public void finished(Void result) {}
    
    }

}
