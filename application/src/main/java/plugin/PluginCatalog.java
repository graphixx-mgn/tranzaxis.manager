package plugin;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;

final class PluginCatalog extends Catalog {

    private final static String PROP_ON_UPDATE = "onUpdate";

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
                title,
                null
        );

        // Properties
        model.addUserProp(PROP_ON_UPDATE, new Enum<>(OnUpdate.Install), true, Access.Select);
    }

    @Override
    public void attach(INode child) {
        super.attach(child);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return PackageView.class;
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

    OnUpdate onUpdateOption() {
        return (OnUpdate) model.getValue(PROP_ON_UPDATE);
    }

    PackageView getView(PluginPackage pluginPackage) {
        return childrenList().stream()
                .map(iNode -> (PackageView) iNode)
                .filter(packageView -> packageView.getPackage().equals(pluginPackage))
                .findFirst().orElse(null);
    }


    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        PackageView   packageView = (PackageView) entity;
        PluginPackage pluginPackage = packageView.getPackage();
        try {
            if (packageView.isPublished()) {
                packageView.getCommand(PackageView.PublishPackage.class).execute(packageView, Collections.emptyMap());
            }
            PluginManager.getInstance().getPluginLoader().removePluginPackage(pluginPackage, true, true);
            for (PluginHandler pluginHandler : pluginPackage.getPlugins()) {
                Entity.deleteInstance(pluginHandler.getView(), false, false);
            }
            Entity.deleteInstance(entity, false, false);
        } catch (PluginException | IOException e) {
            Logger.getLogger().warn(MessageFormat.format("Unable to remove plugin package ''{0}''", pluginPackage), e);
            if (e instanceof PluginException && ((PluginException) e).isHandled()) return;
            MessageBox.show(
                    MessageType.WARNING,
                    MessageFormat.format(
                            Language.get(PackageView.class, "delete@error"),
                            e.getMessage()
                    )
            );
        }
    }


    enum OnUpdate implements Iconified {

        Install(ImageUtils.getByPath("/images/plugin_install.png")),
        Notify(ImageUtils.getByPath("/images/notify.png"));

        private final ImageIcon icon;
        private final String title;

        OnUpdate(ImageIcon icon) {
            this.icon  = icon;
            this.title = Language.get(PluginManager.class, "update@".concat(name().toLowerCase()));
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }


        @Override
        public String toString() {
            return title;
        }
    }
}
