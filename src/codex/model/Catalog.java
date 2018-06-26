package codex.model;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.service.ServiceRegistry;
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
     * @param icon Иконка каталога для отображения в дереве проводника.
     * @param hint Подсказка о назначении каталога.
     */
    public Catalog(ImageIcon icon, String hint) {
        this(icon, "title", hint);
    }
    
    public Catalog(ImageIcon icon, String title, String hint) {
        super(icon, title, hint);
    }
    
    public Catalog(INode parent, ImageIcon icon, String title, String hint) {
        super(parent, icon, title, hint);
    }
    
    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        loadChildEntities();
    }
    
    private void loadChildEntities() {
        if (getChildClass() != null) {
            Map<Integer, String> rowsData = STORE.readCatalogEntries(getChildClass());
            rowsData.forEach((ID, PID) -> {
                Entity entity = Entity.newInstance(getChildClass(), this, PID);
            });
        }
    }
    
    @Override
    public abstract Class getChildClass();

}
