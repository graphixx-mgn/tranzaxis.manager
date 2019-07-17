package codex.config;

import codex.model.Access;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import javax.swing.*;

class EntityProxy extends Catalog {

    static String PROP_EXPORT = "export";
    private static final ImageIcon CATALOG = ImageUtils.getByPath("/images/folder.png");

    private final Entity entity;

    EntityProxy(EntityRef ref, String title) {
        super(
                null,
                ref == null ? CATALOG : ref.getValue().getIcon(),
                title,
                null
        );
        this.entity = ref == null ? null : ref.getValue();

        // Properties
        model.addUserProp(PROP_EXPORT, new Bool(true), false, Access.Edit);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    public Entity getEntity() {
        return entity;
    }

    EntityProxy setExport(boolean export) {
        model.setValue(PROP_EXPORT, export);
        return this;
    }

}
