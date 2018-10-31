package manager.nodes;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.log.ILogMgmtService;
import codex.log.Level;
import codex.log.LogUnit;
import codex.mask.DirMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.service.ServiceRegistry;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import manager.Manager;
import manager.commands.common.DiskUsageReport;
import manager.type.Locale;

public final class Common extends Catalog {
    
    private final Preferences   PREFERENCES    = Preferences.userRoot().node(Manager.class.getSimpleName());
    
    public  final static String PROP_WORK_DIR  = "workDir";
    public  final static String PROP_LOG_LEVEL = "logLevel";
    public  final static String PROP_GUI_LANG  = "guiLang";
    public  final static String PROP_USE_TRAY  = "useTray";

    public Common() {
        super(null, ImageUtils.getByPath("/images/settings.png"), "title", Language.get("desc"));
        // Properties
        model.addUserProp(PROP_WORK_DIR,  new FilePath(null).setMask(new DirMask()), true, Access.Select);
        model.addUserProp(PROP_LOG_LEVEL, new Enum(Level.Debug), false, Access.Select);
        model.addUserProp(PROP_GUI_LANG,  new Enum(Locale.valueOf(Language.getLocale())), false, Access.Select);
        //model.addUserProp(PROP_USE_TRAY,  new Bool(false), false, Access.Select);

        // Editor settings
        //model.getEditor(PROP_USE_TRAY).setEditable(SystemTray.isSupported());

        // Handlers
        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach((propName) -> {
                    switch (propName) {
                        case PROP_WORK_DIR:
                            childrenList().forEach((child) -> {
                                setChildMode(child, getWorkDir() != null);
                            });
                            break;
                        
                        case PROP_GUI_LANG:
                            PREFERENCES.put(propName, ((Locale) model.getValue(propName)).name());
                            SwingUtilities.invokeLater(() -> {
                                MessageBox.show(MessageType.INFORMATION, Language.get(Common.class.getSimpleName(), "guiLang.notify"));
                            });
                            break;
                            
//                        case "useTray":
//                            prefs.putBoolean(propName, (boolean) model.getValue(propName));
//                            break;
                            
                        case PROP_LOG_LEVEL:
                            Level minLevel = (Level) model.getValue(propName);
                            ILogMgmtService logMgmt = (ILogMgmtService) ServiceRegistry.getInstance().lookupService(LogUnit.LogMgmtService.class);
                            Map<Level, Boolean> levelMap = new HashMap<>();
                            EnumSet.allOf(Level.class).forEach((level) -> {
                                levelMap.put(level, level.ordinal() >= minLevel.ordinal());
                            });
                            logMgmt.changeLevels(levelMap);
                            PREFERENCES.put(propName, ((Level) model.getValue(propName)).name());
                            break;
                    }
                });          
            }
        });
        
        // Commands
        addCommand(new DiskUsageReport());
    }
    
    public final Path getWorkDir() {
        return (Path) model.getValue(PROP_WORK_DIR);
    }
    
    public final Level getLogLevel() {
        return (Level) model.getValue(PROP_LOG_LEVEL);
    }
    
    public final Locale getGuiLang() {
        return (Locale) model.getValue(PROP_GUI_LANG);
    }
    
//    public Boolean getUseTray() {
//        return (Boolean) model.getValue(PROP_USE_TRAY);
//    }
    
    public final Common setWorkDir(Path value) {
        model.setValue(PROP_WORK_DIR, value);
        return this;
    }
    
    public final Common setLogLevel(Level value) {
        model.setValue(PROP_LOG_LEVEL, value);
        return this;
    }
    
    public final Common setGuiLang(Locale value) {
        model.setValue(PROP_GUI_LANG, value);
        return this;
    }
    
//    public Common setUseTray(Boolean value) {
//        model.setValue(PROP_USE_TRAY, value);
//        return this;
//    }

    @Override
    public void insert(INode child) {
        super.insert(child);
        setChildMode(child, getWorkDir() != null);
    }
    
    @Override
    public Class getChildClass() {
        return null;
    }
    
    private void setChildMode(INode node, boolean enabled) {
        node.setMode(enabled ? MODE_ENABLED + MODE_SELECTABLE : MODE_NONE);
    }
    
}