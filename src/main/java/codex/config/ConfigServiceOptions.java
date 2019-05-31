package codex.config;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.Language;

public class ConfigServiceOptions extends CommonServiceOptions {
    
    public final static String PROP_DB_FILE  = "dbFile";
    public final static String PROP_SHOW_SQL = "showSql";

    public ConfigServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addDynamicProp(PROP_DB_FILE, new Str(null), Access.Select, () -> {
            return System.getProperty("user.home")+ConfigServiceOptions.this.getService().getOption("file");
        });
        model.addUserProp(PROP_SHOW_SQL,   new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(CommonServiceOptions.class, "debug@options"), PROP_SHOW_SQL);
    }
    
    public final boolean isShowSQL() {
        return model.getValue(PROP_SHOW_SQL) == Boolean.TRUE;
    }
    
}
