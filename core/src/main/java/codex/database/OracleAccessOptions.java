package codex.database;

import codex.service.LocalServiceOptions;
import codex.utils.ImageUtils;

public class OracleAccessOptions extends LocalServiceOptions<OracleAccessService> {

    public OracleAccessOptions(OracleAccessService service) {
        super(service);
        setIcon(ImageUtils.getByPath("/images/database.png"));
    }
}
