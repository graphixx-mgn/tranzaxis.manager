package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import java.util.List;
import javax.swing.ImageIcon;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {
    
    private final static IConfigStoreService STORE = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    /**
     * Конструктор каталога
     * @param icon
     * @param hint 
     */
    public Catalog(ImageIcon icon, String hint) {
        super(icon, "title", hint);
    }
    
    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        loadChildEntities();
    }
    
    private void loadChildEntities() {
        if (getChildClass() != null) {
            final List<String> PIDs = STORE.readCatalogEntries(getChildClass());
            PIDs.forEach((PID) -> {
                final Entity newEntity = Entity.newInstance(getChildClass(), PID);
                insert(newEntity);
            });
        }
    }
    
    @Override
    public abstract Class getChildClass();

}
