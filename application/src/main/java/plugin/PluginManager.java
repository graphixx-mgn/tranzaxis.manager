package plugin;

import codex.component.dialog.Dialog;
import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.IInstanceDispatcher;
import codex.log.Logger;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.xml.Version;
import manager.utils.Versioning;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public final class PluginManager extends AbstractUnit {

    private final static ImageIcon ICON_INFO = ImageUtils.getByPath("/images/info.png");

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

        {
            IInstanceDispatcher localICS = ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class);
            getPackages().stream()
                    .filter(pluginPackage -> Entity.newInstance(PackageView.class, null, pluginPackage.getTitle()).isPublished())
                    .map(IPluginLoaderService.RemotePackage::new)
                    .forEach(remotePackage -> {
                        localICS.getInstances().forEach(instance -> {
                            try {
                                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                                pluginLoader.packagePublicationChanged(remotePackage, true);
                            } catch (RemoteException | NotBoundException e) {
                                e.printStackTrace();
                            }
                        });
                    });
        }

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
        } catch (Exception e) {
            //
        }

        ServiceRegistry.getInstance().addRegistryListener(IInstanceDispatcher.class, service -> {
            IInstanceDispatcher localICS = (IInstanceDispatcher) service;
            localICS.registerRemoteService(PluginLoaderService.class);
            try {
                PluginLoaderService pluginLoaderService = (PluginLoaderService) localICS.getService(PluginLoaderService.class);
                pluginLoaderService.addPublicationListener(pluginCatalog.getCommand(ShowPackagesUpdates.class));
            } catch (RemoteException e) {
                Logger.getLogger().warn("Unable to find plugin loader service", e);
            } catch (NotBoundException ignore) {
                ignore.printStackTrace();
            }
        });

        getPluginLoader().addListener(pluginCatalog.getCommand(ShowPackagesUpdates.class));
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
    }

    static void showVersionInfo(List<Version> versions) {
        JTextPane infoPane = new JTextPane() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        infoPane.setEditable(false);
        infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoPane.setContentType("text/html");
        infoPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(infoPane);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new LineBorder(Color.GRAY, 1)
        ));

        infoPane.setText(Versioning.getChangesHtml(versions));
        infoPane.setPreferredSize(new Dimension(600, infoPane.getPreferredSize().height));

        JPanel content = new JPanel(new BorderLayout());
        content.add(scrollPane, BorderLayout.CENTER);

        new codex.component.dialog.Dialog(
                null,
                ICON_INFO,
                Language.get(PluginManager.class, "history@dialog"),
                content,
                null,
                Dialog.Default.BTN_CLOSE.newInstance()
        ).setVisible(true);
    }
}