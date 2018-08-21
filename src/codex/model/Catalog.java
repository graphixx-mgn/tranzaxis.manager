package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import java.text.MessageFormat;
import java.util.Collection;
import javax.swing.ImageIcon;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {
    
    private final static IConfigStoreService  CSS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    private final static ITaskExecutorService TES = (ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class);
    
    /**
     * Конструктор каталога
     * @param icon Иконка каталога для отображения в дереве проводника.
     * @param hint Подсказка о назначении каталога.
     */
    public Catalog(ImageIcon icon, String hint) {
        this(icon, "title", hint);
    }
    
    public Catalog(ImageIcon icon, String title, String hint) {
        super(null, icon, title, hint);
    }
    
    public Catalog(INode parent, ImageIcon icon, String title, String hint) {
        super(parent, icon, title, hint);
    }
    
    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (getChildClass() != null) {
            try {
                getLock().acquire();
                TES.enqueueTask(new LoadChildren());
            } catch (InterruptedException e) {}
        }
    }
    
    @Override
    public abstract Class getChildClass();
    
    protected Collection<String> getChildrenPIDs() {
        Entity owner = Entity.getOwner(getParent());
        return CSS.readCatalogEntries(owner == null ? null : owner.model.getID(), getChildClass()).values();
    }
    
    private class LoadChildren extends AbstractTask<Void> {

        public LoadChildren() {
            super(MessageFormat.format(
                    "Load children: {0}",
                    Catalog.this.getPathString()
            ));
        }

        @Override
        public Void execute() throws Exception {
            setMode(INode.MODE_NONE);
            getChildrenPIDs().forEach((PID) -> {
                Entity.newInstance(getChildClass(), Catalog.this, PID);
            });
            return null;
        }

        @Override
        public void finished(Void result) {
            setMode(INode.MODE_ENABLED + INode.MODE_SELECTABLE);
            getLock().release();
        }
    
    }

}
