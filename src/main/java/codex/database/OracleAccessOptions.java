package codex.database;

import codex.service.LocalServiceOptions;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class OracleAccessOptions extends LocalServiceOptions<OracleAccessService> {

    public OracleAccessOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/database.png"));
    }
}
