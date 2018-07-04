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
import codex.type.Bool;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.SystemTray;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import manager.Manager;
import manager.type.Locale;

public class Common extends Catalog {
    
    private final Preferences      prefs  = Preferences.userRoot().node(Manager.class.getSimpleName());
    private final java.util.Locale locale = new java.util.Locale(
            java.lang.System.getProperty("user.language"), 
            java.lang.System.getProperty("user.country")
    );

    public Common() {
        super(null, ImageUtils.getByPath("/images/settings.png"), "title", Language.get("desc"));
        model.addUserProp("workDir",  new FilePath(null).setMask(new DirMask()), true, Access.Select);
        model.addUserProp("logLevel", new Enum(Level.Debug), false, Access.Select);
        model.addUserProp("guiLang",  new Enum(Locale.valueOf(locale)), false, Access.Select);
        model.addUserProp("useTray",  new Bool(false), false, Access.Select);

        model.getEditor("useTray").setEditable(SystemTray.isSupported());

        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach((propName) -> {
                    switch (propName) {
                        case "workDir":
                            childrenList().forEach((child) -> {
                                enableChildNode(child, true);
                            });
                            break;
                        case "guiLang":
                            prefs.put(propName, ((Locale) model.getValue(propName)).name());
                            SwingUtilities.invokeLater(() -> {
                                MessageBox.show(MessageType.INFORMATION, Language.get(Common.class.getSimpleName(), "guiLang.notify"));
                            });
                            break;
                        case "useTray":
                            prefs.putBoolean(propName, (boolean) model.getValue(propName));
                            break;
                        case "logLevel":
                            Level minLevel = (Level) model.getValue(propName);
                            ILogMgmtService logMgmt = (ILogMgmtService) ServiceRegistry
                                    .getInstance()
                                    .lookupService(LogUnit.LogMgmtService.class);
                            Map<Level, Boolean> levelMap = new HashMap<>();
                            EnumSet.allOf(Level.class).forEach((level) -> {
                                levelMap.put(level, level.ordinal() >= minLevel.ordinal());
                            });
                            logMgmt.changeLevels(levelMap);
                            prefs.put(propName, ((Level) model.getValue(propName)).name());
                            break;
                    }
                });
                
            }
        });
    }

    @Override
    public void insert(INode child) {
        super.insert(child);
        enableChildNode(child, model.getValue("workDir") != null);
    }
    
    @Override
    public Class getChildClass() {
        // Нет селектора
        return null;
    }
    
    private void enableChildNode(INode node, boolean enabled) {
        node.setMode(enabled ? MODE_ENABLED + MODE_SELECTABLE : MODE_NONE);
    }
    
}