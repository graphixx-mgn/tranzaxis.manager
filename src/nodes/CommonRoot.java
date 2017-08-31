package nodes;


import codex.explorer.tree.AbstractNode;
import codex.log.Level;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Path;
import type.Locale;

public class CommonRoot extends AbstractNode {

    public CommonRoot() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder(Path.class,  "workDir",  null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(Level.class, "logLevel", Level.Debug, false), Access.Select);
        model.addPersistProperty(new PropertyHolder(Locale.class, "guiLang", Locale.Russian, false), Access.Select);
    }
    
}