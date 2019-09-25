package manager.type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.util.EnumSet;
import javax.swing.ImageIcon;

public enum Locale implements Iconified {
    
    Russian("Русский", ImageUtils.getByPath("/images/rus.png"), new java.util.Locale("ru", "RU")),
    English("English", ImageUtils.getByPath("/images/eng.png"), new java.util.Locale("en", "US"));
    
    private final String    title;
    private final ImageIcon icon;
    private final java.util.Locale locale;
    
    private Locale(String title, ImageIcon icon, java.util.Locale locale) {
        this.title  = title;
        this.icon   = icon;
        this.locale = locale;
    }
    
    @Override
    public ImageIcon getIcon() {
        return icon;
    }
    
    public java.util.Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return title;
    }
    
    public static Locale valueOf(java.util.Locale locale) {
        for (Locale localeItem : EnumSet.allOf(Locale.class)) {
            if (localeItem.locale.equals(locale)) {
                return localeItem;
            }
        }
        return English;
    }
    
}
