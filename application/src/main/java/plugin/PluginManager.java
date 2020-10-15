package plugin;

import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceDispatcher;
import codex.log.Logger;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Collection;

public final class PluginManager extends AbstractUnit {

    private static final PluginManager INSTANCE = new PluginManager();
    public static PluginManager getInstance() {
        return INSTANCE;
    }

    static Object getOption(Class <? extends IPlugin> pluginClass, String optName) throws ClassNotFoundException {
        return getInstance().getPluginLoader().getPackages().parallelStream()
                .map(PluginPackage::getPlugins)
                .flatMap(Collection::stream)
                .filter(pluginHandler -> pluginHandler.getPluginClass().equals(pluginClass))
                .findFirst()
                .orElseThrow(() -> new ClassNotFoundException(pluginClass.getCanonicalName()))
                .getView()
                .getOption(optName);
    }

    private ExplorerUnit        explorer;
    private final PluginCatalog pluginCatalog = new PluginCatalog();
    private final PluginLoader  pluginLoader  = new PluginLoader() {

        @Override
        void addPluginPackage(PluginPackage pluginPackage) {
            super.addPluginPackage(pluginPackage);
            pluginCatalog.attach(new PackageView(pluginPackage));
        }

        @Override
        void replacePluginPackage(PluginPackage pluginPackage) {
            super.replacePluginPackage(pluginPackage);
            PluginPackage installedPackage = getPackageById(pluginPackage.getId());
            pluginCatalog.childrenList().stream()
                    .map(iNode -> (PackageView) iNode)
                    .filter(packageView -> packageView.getPackage().equals(installedPackage))
                    .findFirst()
                    .ifPresent(packageView -> {
                        int position = pluginCatalog.getIndex(packageView);
                        pluginCatalog.replace(new PackageView(pluginPackage), position);
                    });
        }
    };

    private PluginManager() {
        Logger.getLogger().debug("Initialize unit: Plugin Manager");
        try {
            Constructor ctor = ExplorerUnit.class.getDeclaredConstructor(BrowseMode.class);
            ctor.setAccessible(true);
            explorer = (ExplorerUnit) ctor.newInstance(new EmbeddedMode());
            explorer.createViewport();

            Field navigatorField = ExplorerUnit.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);

            Navigator navigator = (Navigator) navigatorField.get(explorer);
            navigator.setModel(new NodeTreeModel(pluginCatalog));
        } catch (Exception ignore) {}
    }

    PluginCatalog getPluginCatalog() {
        return pluginCatalog;
    }

    PluginLoader getPluginLoader() {
        return pluginLoader;
    }

    @Override
    public JComponent createViewport() {
        return explorer.getViewport();
    }

    @Override
    public void viewportBound() {
        explorer.viewportBound();

        CommandRegistry.getInstance().registerCommand(PluginCatalog.class, ShowPackagesUpdates.class);

        ServiceRegistry.getInstance().addRegistryListener(IInstanceDispatcher.class, service -> {
            IInstanceDispatcher localICS = (IInstanceDispatcher) service;
            localICS.registerRemoteService(PluginLoaderService.class);

            try {
                PluginLoaderService localPluginLoader = (PluginLoaderService) localICS.getService(PluginLoaderService.class);
                localPluginLoader.addPublicationListener(pluginCatalog.getCommand(ShowPackagesUpdates.class));
            } catch (RemoteException e) {
                Logger.getLogger().warn("Unable to find plugin loader service", e);
            } catch (NotBoundException ignore) {}

            pluginLoader.getPackages().stream()
                    .filter(pluginPackage -> Entity.newInstance(PackageView.class, null, pluginPackage.getTitle()).isPublished())
                    .map(IPluginLoaderService.RemotePackage::new)
                    .forEach(remotePackage -> {
                        localICS.getInstances().forEach(instance -> {
                            try {
                                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                                pluginLoader.packagePublicationChanged(remotePackage, true);
                            } catch (RemoteException | NotBoundException ignore) {}
                        });
                    });
        });
    }
}