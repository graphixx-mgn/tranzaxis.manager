package codex.database;

import codex.model.Access;
import codex.service.CommonServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.utils.Language;

public class OracleAccessOptions extends CommonServiceOptions {

    public final static String PROP_SHOW_SQL = "showSql";

    public OracleAccessOptions(EntityRef owner, String title) {
        super(owner, title);
        model.addUserProp(PROP_SHOW_SQL,   new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(CommonServiceOptions.class, "debug@options"), PROP_SHOW_SQL);
    }
    
    public final boolean isShowSQL() {
        return model.getValue(PROP_SHOW_SQL) == Boolean.TRUE;
    }
    
}
