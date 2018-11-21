package codex.config;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.EntityRef;
import codex.type.FilePath;
import java.nio.file.Path;


public class ConfigServiceOptions extends CommonServiceOptions {
    
    public  final static String PROP_DB_FILE = "dbFile";
    
    public ConfigServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addDynamicProp(PROP_DB_FILE, new FilePath(null), Access.Select, () -> {
            return null;
        });
    }
    
    public final void setWorkDir(Path value) {
        model.setValue(PROP_DB_FILE, value);
    }
    
}
