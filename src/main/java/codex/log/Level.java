package codex.log;

import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.Language;
import javax.swing.ImageIcon;

public enum Level implements Iconified {
    
    Debug(org.apache.log4j.Level.DEBUG, LogUnit.DEBUG),
    Info(org.apache.log4j.Level.INFO,   LogUnit.INFO),
    Warn(org.apache.log4j.Level.WARN,   LogUnit.WARN),
    Error(org.apache.log4j.Level.ERROR, LogUnit.ERROR);
    
    private final String    title;
    private final ImageIcon icon;
    final org.apache.log4j.Level log4jLevel;
    
    Level(org.apache.log4j.Level level, ImageIcon icon) {
        this.log4jLevel = level;
        this.title = Language.get(LogUnit.class, "level@"+level.toString().toLowerCase()+PropertyHolder.PROP_NAME_SUFFIX, Language.getLocale());
        this.icon  = icon;
    }
    
    public org.apache.log4j.Level getSysLevel() {
        return log4jLevel;
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
