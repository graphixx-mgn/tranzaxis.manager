package nodes;


import codex.explorer.tree.AbstractNode;
import codex.model.Access;
import codex.property.PropertyHolder;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.nio.file.Path;
import org.apache.log4j.Level;

public class CommonRoot extends AbstractNode {

    public CommonRoot() {
        super(ImageUtils.getByPath("/images/settings.png"), Language.get("title"), Language.get("desc"));
        model.addPersistProperty(new PropertyHolder(Path.class,  "workDir",  null, true), Access.Select);
        model.addPersistProperty(new PropertyHolder(Level.class, "logLevel", Level.DEBUG, false), Access.Select);
    }
    
}