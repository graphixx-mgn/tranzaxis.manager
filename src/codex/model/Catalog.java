package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
import java.util.List;
import java.util.Map;
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
            List<Map<String, String>> rowsData = STORE.readCatalogEntries(getChildClass());
            rowsData.forEach((map) -> {
                String title  = map.get(EntityModel.PID);
                Entity entity = Entity.newInstance(getChildClass(), null);
                entity.setTitle(title);
                map.forEach((propName, propVal) -> {
                    entity.model.getProperty(propName).getPropValue().valueOf(propVal);
                });
                entity.model.init();
                insert(entity);
            });
        }
    }
    
    @Override
    public abstract Class getChildClass();

}
