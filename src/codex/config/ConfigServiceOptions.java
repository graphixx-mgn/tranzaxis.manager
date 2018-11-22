package codex.config;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.EntityRef;
import codex.type.Str;


public class ConfigServiceOptions extends CommonServiceOptions {
    
    public  final static String PROP_DB_FILE = "dbFile";
    
    public ConfigServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addDynamicProp(PROP_DB_FILE, new Str(null), Access.Select, () -> {
            return System.getProperty("user.home")+ConfigServiceOptions.this.getService().getOption("file");
        });
    }
    
}
