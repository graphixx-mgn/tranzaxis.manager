package manager.nodes;


import codex.model.Entity;
import codex.log.Level;
import codex.mask.DirMask;
import codex.model.Access;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Paths;
import java.util.prefs.Preferences;
import manager.Manager;
import manager.type.Locale;

public class CommonRoot extends Entity {
    
    private final Preferences prefs = Preferences.userRoot().node(Manager.class.getSimpleName());
    
    private final FilePath workDir  = new FilePath("".equals(prefs.get("workDir", "")) ? null : Paths.get(prefs.get("workDir", "")));
    private final Bool     useTray  = new Bool(prefs.getBoolean("useTray", false));
    private final Enum     logLevel = new Enum(Level.valueOf(prefs.get("logLevel", Level.Info.name())));
    private final Enum     guiLang  = new Enum(Locale.valueOf(prefs.get("guiLang", Locale.English.name())));

    public CommonRoot() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("title"), Language.get("desc"));
        model.addProperty("workDir",  workDir.setMask(new DirMask()), true, Access.Select, true);
        model.addProperty("logLevel", logLevel, false, Access.Select, true);
        model.addProperty("guiLang",  guiLang, false, Access.Select, true);
        model.addProperty("useTray",  useTray, false, Access.Select, true);
    }

//    @Override
//    public void propertyChange(String name, Object oldValue, Object newValue) {
//        java.lang.System.out.println("RID: "+name);
//        java.lang.System.out.println("VAL: "+model.getValue(name));
//        super.propertyChange(name, oldValue, newValue);
//        
//        if (java.lang.Enum.class.isAssignableFrom(newValue.getClass())) {
//            prefs.put(name, ((java.lang.Enum) newValue).name());
//        } else {
//            prefs.put(name, newValue.toString());
//        }
//    }
    
}