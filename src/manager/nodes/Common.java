package manager.nodes;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.mask.DirMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import manager.Manager;
import manager.commands.common.DiskUsageReport;
import manager.type.Locale;

public final class Common extends Catalog {
    
    private final Preferences   PREFERENCES    = Preferences.userRoot().node(Manager.class.getSimpleName());
    
    public  final static String PROP_WORK_DIR  = "workDir";
    public  final static String PROP_GUI_LANG  = "guiLang";
    public  final static String PROP_USE_TRAY  = "useTray";

    public Common() {
        super(null, ImageUtils.getByPath("/images/settings.png"), "title", Language.get("desc"));
        // Properties
        model.addUserProp(PROP_WORK_DIR,  new FilePath(null).setMask(new DirMask()), true, Access.Select);
        model.addUserProp(PROP_GUI_LANG,  new Enum(Locale.valueOf(Language.getLocale())), false, Access.Select);

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
    
    public final Locale getGuiLang() {
        return (Locale) model.getValue(PROP_GUI_LANG);
    }
    
    public final Common setWorkDir(Path value) {
        model.setValue(PROP_WORK_DIR, value);
        return this;
    }
    
    public final Common setGuiLang(Locale value) {
        model.setValue(PROP_GUI_LANG, value);
        return this;
    }

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