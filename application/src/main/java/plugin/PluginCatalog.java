package plugin;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.instance.IInstanceDispatcher;
import codex.log.Logger;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;

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


    public static <E extends Entity> void deleteInstance(E entity, boolean cascade, boolean confirmation) {
        PackageView   pkgView = (PackageView) entity;
        PluginPackage pkg = ((PackageView) entity).getPackage();

        try {
            if (pkgView.isPublished()) {
                if (ServiceRegistry.getInstance().isServiceRegistered(IInstanceDispatcher.class)) {
                    IInstanceDispatcher ICS = ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class);
                    if (ICS.isStarted()) {
                        ICS.getInstances().forEach(instance -> {
                            try {
                                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                                pluginLoader.packagePublicationChanged(
                                        new IPluginLoaderService.RemotePackage(pkg),
                                        false
                                );
                            } catch (RemoteException | NotBoundException ignore) {
                                //
                            }
                        });
                    }
                }
            }
            for (PluginHandler pluginHandler : pkg.getPlugins()) {
                Entity.deleteInstance(pluginHandler.getView(), false, false);
            }
            Entity.deleteInstance(pkgView, false, false);
            PluginManager.getInstance().getPluginLoader().removePluginPackage(pkg, true);
        } catch (PluginException | IOException e) {
            Logger.getLogger().warn("Unable to remove plugin package ''{0}'': {1}", pkg, e.getMessage());
            MessageBox.show(
                    MessageType.WARNING,
                    MessageFormat.format(
                            Language.get(PackageView.class, "delete@error"),
                            e.getMessage()
                    )
            );
            pkgView.updateView();
        }
    }
}
