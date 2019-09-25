package plugin;

import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;

final class PluginCatalog extends Catalog {

    static {
        CommandRegistry.getInstance().registerCommand(ShowPackagesUpdates.class);
    }

    PluginCatalog() {
        this(null, null);
    }

    private PluginCatalog(EntityRef owner, String title) {
        super(
                null,
                ImageUtils.getByPath("/images/plugins.png"),
                Language.get(PluginManager.class, "root@title"),
                null
        );
    }

    @Override
    public void insert(INode child) {
        super.insert(child);
        getCommand(ShowPackagesUpdates.class).activate();
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return PackageView.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }
}
