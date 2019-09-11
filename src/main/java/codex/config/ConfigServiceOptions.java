package codex.config;

import codex.model.Access;
import codex.service.LocalServiceOptions;
import codex.type.Str;
import codex.utils.ImageUtils;

public class ConfigServiceOptions extends LocalServiceOptions<ConfigStoreService> {

    private final static String PROP_DB_FILE  = "dbFile";

    public ConfigServiceOptions(ConfigStoreService service) {
        super(service);
        setIcon(ImageUtils.getByPath("/images/config.png"));
        model.addDynamicProp(PROP_DB_FILE, new Str(null), Access.Select, () -> {
            return System.getProperty("user.home")+ConfigServiceOptions.this.getService().getOption("file");
        });
    }
}
