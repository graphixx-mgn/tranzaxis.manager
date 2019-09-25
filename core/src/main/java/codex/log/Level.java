package codex.log;

import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.Language;
import javax.swing.ImageIcon;

public enum Level implements Iconified {
    
    Debug(org.apache.log4j.Level.DEBUG, Logger.DEBUG),
    Info(org.apache.log4j.Level.INFO,   Logger.INFO),
    Warn(org.apache.log4j.Level.WARN,   Logger.WARN),
    Error(org.apache.log4j.Level.ERROR, Logger.ERROR),
    Off(org.apache.log4j.Level.OFF,     Logger.OFF);
    
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

    static Level fromSysLevel(org.apache.log4j.Level sysLevel) {
        for (Level level : Level.values()) {
            if (level.getSysLevel() == sysLevel) {
                return level;
            }
        }
        throw new IllegalStateException("There is no suitable level for '"+sysLevel+"'");
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
