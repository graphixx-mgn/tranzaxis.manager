package codex.model;

import codex.type.EntityRef;
import javax.swing.*;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity {

    public Catalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }
    
    @Override
    public abstract Class<? extends Entity> getChildClass();

}
