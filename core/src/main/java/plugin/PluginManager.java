package plugin;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.model.Catalog;
import codex.model.Entity;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class PluginManager extends AbstractUnit {

    private static final File PLUGIN_DIR = new File("plugins");
    private static final PluginManager INSTANCE = new PluginManager();
    public static PluginManager getInstance() {
        return INSTANCE;
    }
    static {
        PLUGIN_DIR.mkdirs();
    }

    private ExplorerUnit       explorer;
    private final Catalog      pluginCatalog = new PluginCatalog();
    private final PluginLoader pluginLoader = new PluginLoader(PLUGIN_DIR) {
        @Override
        void addPluginPackage(PluginPackage pluginPackage) {
            super.addPluginPackage(pluginPackage);
            pluginCatalog.insert(new PackageView(pluginPackage));
        }
    };

    private PluginManager() {
        Logger.getLogger().debug("Initialize unit: Plugin Manager");
        try {
            Constructor ctor = ExplorerUnit.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            explorer = (ExplorerUnit) ctor.newInstance();
            explorer.createViewport();

            Field navigatorField = ExplorerUnit.class.getDeclaredField("navigator");
            navigatorField.setAccessible(true);

            Navigator navigator = (Navigator) navigatorField.get(explorer);
            navigator.setModel(new NodeTreeModel(pluginCatalog));
        } catch (Exception e) {
            //
        }
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
    }


    class PluginCatalog extends Catalog {

        PluginCatalog() {
            super(null,
                    ImageUtils.getByPath("/images/plugins.png"),
                    Language.get(PluginManager.class, "root@title"),
                    null
            );
            //addCommand(new LookupPlugins());
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
}
