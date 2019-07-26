package plugin;

import codex.command.EditorCommand;
import codex.component.dialog.Dialog;
import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.editor.IEditorFactory;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.UpgradeService;
import manager.upgrade.UpgradeUnit;
import manager.xml.Change;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

class RemotePackageView extends Catalog {

    private final static ImageIcon ICON_CREATE = ImageUtils.getByPath("/images/plus.png");
    private final static ImageIcon ICON_UPDATE = ImageUtils.getByPath("/images/up.png");
    private final static ImageIcon ICON_INFO   = ImageUtils.resize(ImageUtils.getByPath("/images/info.png"),20,20);
    static {
        CommandRegistry.getInstance().registerCommand(DownloadPackages.class);
    }

    final static String PROP_VERSION = "version";
    final static String PROP_UPGRADE = "upgrade";
    final static String PROP_AUTHOR  = "author";

    PluginLoaderService.RemotePackage remotePackage;

    RemotePackageView(PluginLoaderService.RemotePackage remotePackage) {
        this(null, remotePackage.getTitle());
        this.remotePackage = remotePackage;
        setIcon(remotePackage.getIcon());

        if (remotePackage.getPlugins().size() == 1) {
            IPluginLoaderService.RemotePlugin remotePlugin = remotePackage.getPlugins().get(0);

            remotePlugin.getProperties().stream()
                    .filter(propPresentation -> propPresentation.getAccess().equals(Access.Select))
                    .forEach(propPresentation -> {
                        String propName = propPresentation.getName();
                        model.addDynamicProp(
                                propName,
                                new AnyType() {
                                    @Override
                                    public IEditorFactory editorFactory() {
                                        return propHolder -> {
                                            try {
                                                Constructor<? extends IEditor> ctor = propPresentation.getEditor().getConstructor(PropertyHolder.class);
                                                return ctor.newInstance(propHolder);
                                            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                                                e.printStackTrace();
                                                return new AnyTypeView(propHolder);
                                            }
                                        };
                                    }
                                }, Access.Select, () -> new Iconified() {
                                    @Override
                                    public ImageIcon getIcon() {
                                        return propPresentation.getIcon();
                                    }

                                    @Override
                                    public String toString() {
                                        return propPresentation.getValue();
                                    }
                                });
                        String propTitle = Language.get(
                                propName.equals(Plugin.PROP_TYPE) ? Plugin.class : remotePlugin.getHandlerClass(),
                                propName+PropertyHolder.PROP_NAME_SUFFIX
                        );
                        String propDesc = Language.get(
                                propName.equals(Plugin.PROP_TYPE) ? Plugin.class : remotePlugin.getHandlerClass(),
                                propName+PropertyHolder.PROP_DESC_SUFFIX
                        );
                        PackageView.changePropertyNaming(model.getProperty(propName), propTitle, propDesc);
                    });
        } else {
            remotePackage.getPlugins().forEach(remotePlugin -> insert(new RemotePluginView(remotePlugin)));
        }

        List<Version> diffVersions = getChanges(remotePackage);
        if (!diffVersions.isEmpty()) {
            ((AnyTypeView) model.getEditor("version")).addCommand(new ShowChanges(diffVersions));
        }
    }

    RemotePackageView(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/repository.png"), title, null);

        // Properties
        model.addDynamicProp(PROP_VERSION, new AnyType(), Access.Select, () -> remotePackage.getVersion());
        model.addDynamicProp(PROP_UPGRADE, new AnyType(), Access.Edit, () -> {
            final PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
            return new Iconified() {
                @Override
                public ImageIcon getIcon() {
                    return localPackage == null ? ICON_CREATE : ICON_UPDATE;
                }

                @Override
                public String toString() {
                    return localPackage == null ? remotePackage.getVersion() : MessageFormat.format(
                            "<html>{0} &rarr; {1}</html>",
                            localPackage.getVersion(),
                            remotePackage.getVersion()
                    );
                }
            };
        });
        model.addDynamicProp(PROP_AUTHOR,  new AnyType(), null, () -> remotePackage.getAuthor());
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return RemotePluginView.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    void refreshUpgradeInfo() {
        model.setValue(PROP_UPGRADE, model.calculateDynamicValue(PROP_UPGRADE));
    }

    static List<Version> getChanges(PluginLoaderService.RemotePackage remotePackage) {
        List<Version> changes = new LinkedList<>();
        final PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
        if (localPackage != null && PluginPackage.VER_COMPARATOR.compare(remotePackage.getVersion(), localPackage.getVersion()) > 0) {
            VersionsDocument remotePkgVersions = remotePackage.getChanges();
            if (remotePkgVersions != null) {
                Version localVersion = Version.Factory.newInstance();
                localVersion.setNumber(localPackage.getVersion());
                for (Version remoteVersion : remotePackage.getChanges().getVersions().getVersionArray()) {
                    if (UpgradeService.VER_COMPARATOR.compare(remoteVersion, localVersion) > 0) {
                        changes.add(remoteVersion);
                    }
                }
            }
        }
        return changes;
    }


    class ShowChanges extends EditorCommand<AnyType, Object> {
        private final List<Version> changes;

        ShowChanges(List<Version> changes) {
            super(ICON_INFO, "[Show changes]");
            this.changes = changes;
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
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
            infoPane.setPreferredSize(new Dimension(500, 200));
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
                for (Version version : changes) {
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
                                        "\u2022 [{0}] {1}\n",
                                        String.format("%4s", change.getScope()),
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

            JPanel content = new JPanel(new BorderLayout());
            content.add(scrollPane, BorderLayout.CENTER);

            new Dialog(
                    null,
                    ICON_INFO,
                    "[Changes]",
                    content,
                    null,
                    Dialog.Default.BTN_CLOSE.newInstance()
            ).setVisible(true);
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
    }
}
