package manager.nodes;


import codex.log.Level;
import codex.mask.DirMask;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.List;
import java.util.prefs.Preferences;
import manager.Manager;
import manager.type.Locale;

public class Common extends Catalog {
    
    private final Preferences      prefs  = Preferences.userRoot().node(Manager.class.getSimpleName());
    private final java.util.Locale locale = new java.util.Locale(
            java.lang.System.getProperty("user.language"), 
            java.lang.System.getProperty("user.country")
    );

    public Common() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("desc"));
        model.addUserProp("workDir",  new FilePath(null).setMask(new DirMask()), true, Access.Select);
        model.addUserProp("logLevel", new Enum(Level.Info), false, Access.Select);
        model.addUserProp("guiLang",  new Enum(Locale.valueOf(locale)), false, Access.Select);
        model.addUserProp("useTray",  new Bool(false), false, Access.Select);

        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach((propName) -> {
                    switch (propName) {
                        case "guiLang": 
                            prefs.put(propName, ((Locale) model.getValue(propName)).name());
                            break;
                    }
                });
                
            }
        });
    }

    @Override
    public Class getChildClass() {
        // Нет селектора
        return null;
    }
    
}