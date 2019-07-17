package codex.config;

import codex.model.Access;
import codex.model.CommandRegistry;
import codex.service.LocalServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class ConfigServiceOptions extends LocalServiceOptions {
    
    public final static String PROP_DB_FILE  = "dbFile";
    public final static String PROP_SHOW_SQL = "showSql";

    static {
        CommandRegistry.getInstance().registerCommand(ExportObjects.class);
        CommandRegistry.getInstance().registerCommand(ImportObjects.class);
    }

    public ConfigServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/config.png"));
        model.addDynamicProp(PROP_DB_FILE, new Str(null), Access.Select, () -> {
            return System.getProperty("user.home")+ConfigServiceOptions.this.getService().getOption("file");
        });
        model.addUserProp(PROP_SHOW_SQL,   new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(LocalServiceOptions.class, "debug@options"), PROP_SHOW_SQL);
    }
    
    public final boolean isShowSQL() {
        return model.getValue(PROP_SHOW_SQL) == Boolean.TRUE;
    }
    
}
