package plugin;

import codex.command.EditorCommand;
import codex.editor.AnyTypeView;
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
import manager.xml.Version;
import manager.xml.VersionsDocument;
import javax.swing.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

class RemotePackageView extends Catalog {

    private final static ImageIcon ICON_SOURCE = ImageUtils.getByPath("/images/localhost.png");
    private final static ImageIcon ICON_CREATE = ImageUtils.getByPath("/images/plus.png");
    private final static ImageIcon ICON_UPDATE = ImageUtils.getByPath("/images/up.png");
    private final static ImageIcon ICON_INFO   = ImageUtils.getByPath("/images/info.png");
    static {
        CommandRegistry.getInstance().registerCommand(DownloadPackages.class);
    }

    private final static String PROP_VERSION = "version";
    private final static String PROP_UPGRADE = "upgrade";
    private final static String PROP_AUTHOR  = "author";
    private final static String PROP_SOURCES = "sources";

    PluginLoaderService.RemotePackage remotePackage;

    RemotePackageView(PluginLoaderService.RemotePackage remotePackage) {
        this(null, remotePackage.getTitle());
        this.remotePackage = remotePackage;
        setIcon(PackageView.PACKAGE);

        remotePackage.getPlugins().forEach(remotePlugin -> attach(new RemotePluginView(remotePlugin)));
        ((AnyTypeView) model.getEditor("version")).addCommand(new ShowChanges());
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
        model.addDynamicProp(PROP_SOURCES, new AnyType(), Access.Edit, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return ICON_SOURCE;
            }

            @Override
            public String toString() {
                return MessageFormat.format(
                        Language.get(RemotePackageView.class, "sources.desc"),
                        remotePackage.getInstances().size()
                );
            }
        });
        model.addDynamicProp(PROP_AUTHOR,  new AnyType(), Access.Select, () -> remotePackage.getAuthor());
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
        model.updateDynamicProps(PROP_SOURCES, PROP_UPGRADE);
    }


    class ShowChanges extends EditorCommand<AnyType, Object> {
        ShowChanges() {
            super(
                    ImageUtils.resize(ICON_INFO,20,20),
                    Language.get(PluginManager.class, "history@command"),
                    propHolder -> remotePackage.getChanges() != null
            );
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
            List<Version> changes = new LinkedList<>();
            final PluginPackage localPackage = PluginManager.getInstance().getPluginLoader().getPackageById(remotePackage.getId());
            if (localPackage != null) {
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
            } else {
                changes.addAll(Arrays.asList(remotePackage.getChanges().getVersions().getVersionArray()));
            }
            PluginManager.showVersionInfo(changes);
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
    }
}
