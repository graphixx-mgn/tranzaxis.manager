package codex.instance;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;

/**
 *
 * @author igredyaev
 */
public class Localhost extends Catalog {
    
    private static final ImageIcon ICON_LOCAL = ImageUtils.getByPath("/images/localhost.png");
    
    public Localhost() {
        super(null, ICON_LOCAL, "[This instance]", null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
}
