package type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;

public enum Locale implements Iconified {
    
    Russian("Русский", ImageUtils.getByPath("/images/rus.png")),
    English("English", ImageUtils.getByPath("/images/eng.png"));
    
    private final String    title;
    private final ImageIcon icon;
    
    private Locale(String title, ImageIcon icon) {
        this.title = title;
        this.icon  = icon;
    }
    
    @Override
    public ImageIcon getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return title;
    }
    
}
