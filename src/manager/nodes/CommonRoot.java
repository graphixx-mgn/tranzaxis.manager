package manager.nodes;


import codex.explorer.tree.Entity;
import codex.log.Level;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.type.Locale;

public class CommonRoot extends Entity {

    public CommonRoot() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder("workDir",  new FilePath(null), true), Access.Select);
        model.addPersistProperty(new PropertyHolder("logLevel", new Enum(Level.Debug), false), Access.Select);
        model.addPersistProperty(new PropertyHolder("guiLang",  new Enum(Locale.Russian), false), Access.Select);
        model.addPersistProperty(new PropertyHolder("useTray",  new Bool(Boolean.FALSE), false), Access.Select);
    }
    
}