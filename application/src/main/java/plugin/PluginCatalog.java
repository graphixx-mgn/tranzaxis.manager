package plugin;

import codex.model.*;
import codex.type.*;
import codex.utils.ImageUtils;
import java.util.*;

final class PluginCatalog extends Catalog {

    static {
        CommandRegistry.getInstance().registerCommand(PluginManager.Refresh.class);
    }

    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        PluginPackage pluginPackage = (PluginPackage) entity;
        PluginManager.getInstance().getProvider().unregister(Collections.singletonList(pluginPackage), true);
    }

    PluginCatalog() {
        this(null, null);
    }

    private PluginCatalog(EntityRef owner, String title) {
        super(
                null,
                ImageUtils.getByPath("/images/plugins.png"),
                title,
                null
        );
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return PluginPackage.class;
    }

    @Override
    public void loadChildren() {}

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }
}
