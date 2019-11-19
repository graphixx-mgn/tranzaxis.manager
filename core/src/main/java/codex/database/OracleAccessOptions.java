package codex.database;

import codex.model.EntityDefinition;
import codex.service.Service;
import codex.type.EntityRef;

@EntityDefinition(icon = "/images/database.png")
public class OracleAccessOptions extends Service<OracleAccessService> {

    public OracleAccessOptions(EntityRef owner, String title) {
        super(owner, title);
    }
}
