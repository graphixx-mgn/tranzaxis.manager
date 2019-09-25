package codex.service;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class RemoteServiceOptions extends Catalog {

    public RemoteServiceOptions(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/services.png"), title, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
}
