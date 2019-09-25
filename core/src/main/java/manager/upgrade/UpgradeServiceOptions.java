package manager.upgrade;

import codex.service.RemoteServiceOptions;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class UpgradeServiceOptions extends RemoteServiceOptions {

    public UpgradeServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/upgrade.png"));
    }
}
