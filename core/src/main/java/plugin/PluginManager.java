package plugin;

import codex.command.ICommandListener;
import codex.component.dialog.Dialog;
import codex.explorer.ExplorerUnit;
import codex.explorer.browser.BrowseMode;
import codex.explorer.browser.EmbeddedMode;
import codex.explorer.tree.Navigator;
import codex.explorer.tree.NodeTreeModel;
import codex.log.Logger;
import codex.notification.Message;
import codex.unit.AbstractUnit;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.UpgradeUnit;
import manager.xml.Change;
import manager.xml.Version;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public final class PluginManager extends AbstractUnit {

    private final static ImageIcon ICON_INFO = ImageUtils.getByPath("/images/info.png");
    //private final static Message   MSG_UPDATE = new Message("UPDATE");

    static final File PLUGIN_DIR = new File("plugins");
    private static final PluginManager INSTANCE = new PluginManager();
    public static PluginManager getInstance() {
        return INSTANCE;
    }
    static {
        PLUGIN_DIR.mkdirs();
    }

    private ExplorerUnit        explorer;
    private final PluginCatalog pluginCatalog = new PluginCatalog();
    private final PluginLoader  pluginLoader = new PluginLoader(PLUGIN_DIR) {
        @Override
        void addPluginPackage(PluginPackage pluginPackage) {
            super.addPluginPackage(pluginPackage);
            pluginCatalog.insert(new PackageView(pluginPackage));
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

            pluginCatalog.getCommand(ShowPackagesUpdates.class).addListener(new ICommandListener<PluginCatalog>() {
                @Override
                public void commandStatusChanged(boolean active) {
                    updateNotifications(navigator.isShowing() && pluginCatalog == navigator.getLastSelectedPathComponent());
                }
            });
            navigator.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                    updateNotifications(navigator.isShowing() && pluginCatalog == navigator.getLastSelectedPathComponent());
                }
                @Override
                public void ancestorRemoved(AncestorEvent event) {}
                @Override
                public void ancestorMoved(AncestorEvent event) {}
            });
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

    private final Set<IPluginLoaderService.RemotePackage> checkedUpdates = new HashSet<>();
    private final Map<IPluginLoaderService.RemotePackage, Message> notifications = new HashMap<>();
    private void updateNotifications(boolean clearAll) {
        List<IPluginLoaderService.RemotePackage> updates = pluginCatalog.getCommand(ShowPackagesUpdates.class).getUpdatedPlugins();
        if (clearAll) {
            checkedUpdates.addAll(updates);
        } else {
            updates.stream()
                    .filter(remotePackage -> !(checkedUpdates.contains(remotePackage) || notifications.containsKey(remotePackage)))
                    .forEach(remotePackage -> {
                            notifications.put(remotePackage, new Message(remotePackage.getId()));
                            eventQueue.putMessage(notifications.get(remotePackage));
                    });
        }
        notifications.entrySet().removeIf(entry -> {
            boolean condition = !updates.contains(entry.getKey()) || checkedUpdates.contains(entry.getKey());
            if (condition) {
                eventQueue.dropMessage(entry.getValue());
            }
            return condition;
        });
    }

    static void showVersionInfo(List<Version> versions) {
        JTextPane infoPane = new JTextPane() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                super.paintComponent(g2);
            }
        };
        infoPane.setEditable(false);
        infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        Style defStyle  = infoPane.addStyle(Change.Type.CHANGE.toString(),  null);
        Style addStyle  = infoPane.addStyle(Change.Type.FEATURE.toString(), defStyle);
        Style fixStyle  = infoPane.addStyle(Change.Type.BUGFIX.toString(),  defStyle);
        Style headStyle = infoPane.addStyle("head", null);

        StyleConstants.setFontSize(headStyle, 14);
        StyleConstants.setUnderline(headStyle, true);
        StyleConstants.setFontFamily(headStyle, "Arial Black");
        StyleConstants.setForeground(addStyle, Color.decode("#00822C"));
        StyleConstants.setForeground(fixStyle, Color.decode("#FF3333"));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setLayout(new ScrollPaneLayout());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getViewport().add(infoPane);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new LineBorder(Color.GRAY, 1)
        ));

        infoPane.setText(null);
        try {
            for (Version version : versions) {
                infoPane.getDocument().insertString(
                        infoPane.getDocument().getLength(),
                        MessageFormat.format(
                                Language.get(UpgradeUnit.class, "info@next"),
                                version.getNumber(), version.getDate()
                        ).concat("\n"), infoPane.getStyle("head")
                );
                for (Change change : version.getChangelog().getChangeArray()) {
                    infoPane.getDocument().insertString(
                            infoPane.getDocument().getLength(),
                            MessageFormat.format(
                                    "\u2022 {0}\n",
                                    change.getDescription().trim().replaceAll("\\n\\s*", " ")
                            ),
                            change.getScope().equals(Change.Scope.API) ?
                                    infoPane.getStyle(Change.Scope.API.toString()) :
                                    infoPane.getStyle(change.getType().toString())
                    );
                }
                infoPane.getDocument().insertString(infoPane.getDocument().getLength(), "\n", infoPane.getStyle(Change.Type.CHANGE.toString()));
            }
        } catch (BadLocationException e1) {
            //
        }
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
