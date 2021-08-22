package plugin;

import codex.command.EntityCommand;
import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class PluginManager extends AbstractUnit implements IPluginService.IPluginServiceListener {

    private static final PluginProvider PXE = new PluginProvider();

    private static final PluginManager INSTANCE = new PluginManager();
    public static PluginManager getInstance() {
        return INSTANCE;
    }

    static {
        ServiceRegistry.getInstance().registerService(PXE);
    }

    static Object getOption(Class <? extends IPlugin> pluginClass, String optName) throws ClassNotFoundException {
        return PXE.getPackages().parallelStream()
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

    @Deprecated
    PluginCatalog getPluginCatalog() {
        return pluginCatalog;
    }

    PluginProvider getProvider() {
        return PXE;
    }

    @Override
    public final JComponent createViewport() {
        return explorer.getViewport();
    }

    @Override
    public final void viewportBound() {
        explorer.viewportBound();
        PXE.addListener(this);
        PXE.readPackages();
    }

    @Override
    public final void packageRegistered(int dataIdx, PluginPackage pluginPackage) {
        SwingUtilities.invokeLater(() -> {
            synchronized (pluginCatalog) {
                pluginCatalog.attach(pluginPackage);
                int viewIdx = pluginCatalog.getIndex(pluginPackage);
                if (dataIdx != viewIdx) {
                    pluginCatalog.move(pluginPackage, dataIdx);
                }
                pluginPackage.getPlugins().forEach(pluginHandler -> {
                    Plugin plugin = pluginHandler.getView();
                    if (plugin.isEnabled() && plugin.isOptionsValid()) {
                        Logger.getContextLogger(PluginProvider.class).debug(
                                "Start previously enabled plugin: {0}",
                                Language.get(pluginHandler.getPluginClass(), "title", Locale.US)
                        );
                        plugin.getCommand(Plugin.LoadPlugin.class).execute(plugin, Collections.emptyMap());
                    }
                });
            }
        });
    }

    @Override
    public void packageUnregistered(int dataIdx, PluginPackage pluginPackage) {
        SwingUtilities.invokeLater(() -> {
            synchronized (pluginCatalog) {
                pluginCatalog.detach(pluginPackage);
            }
        });
    }


    static class Refresh extends EntityCommand<PluginCatalog> {

        private static final String    COMMAND_TITLE = Language.get(PluginCatalog.class, "repo@reload");
        private static final ImageIcon COMMAND_ICON  = ImageUtils.getByPath("/images/update.png");

        public Refresh() {
            super("check repositories", COMMAND_TITLE, COMMAND_ICON, COMMAND_TITLE, null);
        }

        @Override
        public void execute(PluginCatalog context, Map<String, IComplexType> params) {
            PXE.readPackages();
        }

        @Override
        public Kind getKind() {
            return Kind.System;
        }
    }
}