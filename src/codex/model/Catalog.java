package codex.model;

import javax.swing.ImageIcon;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {
    
    /**
     * Конструктор каталога
     * @param icon
     * @param hint 
     */
    public Catalog(ImageIcon icon, String hint) {
        super(icon, "title", hint);
    }
    
    @Override
    public abstract Class getChildClass();
    
}
