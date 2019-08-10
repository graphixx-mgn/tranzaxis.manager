package codex.database;

import codex.model.Access;
import codex.service.LocalServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;

public class OracleAccessOptions extends LocalServiceOptions<OracleAccessService> {

    public final static String PROP_SHOW_SQL = "showSql";

    public OracleAccessOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/database.png"));
        model.addUserProp(PROP_SHOW_SQL,   new Bool(true), false, Access.Select);
        model.addPropertyGroup(Language.get(LocalServiceOptions.class, "debug@options"), PROP_SHOW_SQL);
    }
    
    public final boolean isShowSQL() {
        return model.getValue(PROP_SHOW_SQL) == Boolean.TRUE;
    }
    
}
