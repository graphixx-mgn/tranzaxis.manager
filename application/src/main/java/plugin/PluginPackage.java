package plugin;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.command.EntityCommand;
import codex.config.IConfigStoreService;
import codex.editor.AnyTypeView;
import codex.model.*;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.UpgradeService;
import manager.utils.Versioning;
import manager.xml.VersionsDocument;
import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

class PluginPackage extends Catalog {

    private final static ImageIcon ICON_INFO   = ImageUtils.getByPath("/images/info.png");
    private final static ImageIcon PACKAGE     = ImageUtils.getByPath("/images/repository.png");
    private final static ImageIcon DEVELOPMENT = ImageUtils.getByPath("/images/maintain.png");
    private final static ImageIcon DEPRECATED  = ImageUtils.getByPath("/images/close.png");
    private final static ImageIcon OUTDATED    = ImageUtils.getByPath("/images/up.png");
    private final static ImageIcon PRESENT     = ImageUtils.getByPath("/images/success.png");
    private final static ImageIcon ABSENT      = ImageUtils.grayscale(ImageUtils.getByPath("/images/plus.png"));
    private final static ImageIcon ICON_WARN   = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), .7f);

    private final static String PROP_STATUS    = "status";


    private final static Predicate<PluginPackage> IS_CONFIGURABLE = pluginPackage ->
            pluginPackage.isLoaded() &&
            pluginPackage.getPlugins().size() == 1 &&
            pluginPackage.getPlugins().get(0).getView().hasOptions();
    private final static Predicate<PluginPackage> IS_LOADABLE = pluginPackage ->
            pluginPackage.isLoaded() &&
            pluginPackage.getPlugins().size() == 1 &&
            PackageStatus.getStatusFor(pluginPackage) != PackageStatus.Deprecated;
    private final static Predicate<PluginPackage> IS_UPDATABLE = pluginPackage ->
            Arrays.asList(
                    PackageStatus.Absent,
                    PackageStatus.Deprecated,
                    PackageStatus.Outdated
            ).contains(PackageStatus.getStatusFor(pluginPackage));

    private IPluginRegistry.PackageDescriptor local;
    private IPluginRegistry.PackageDescriptor remote;
    private final IPropertyChangeListener     updater = (name, oldValue, newValue) -> fireChangeEvent();

    private final JPanel singlePluginView = new JPanel(new BorderLayout());

    static {
        CommandRegistry.getInstance().registerCommand(PluginPackage.class, EditOptions.class,      IS_CONFIGURABLE);
        CommandRegistry.getInstance().registerCommand(PluginPackage.class, LoadPlugin.class,       IS_LOADABLE);
        CommandRegistry.getInstance().registerCommand(PluginPackage.class, UnloadPlugin.class,     IS_LOADABLE);
        CommandRegistry.getInstance().registerCommand(PluginPackage.class, DownloadPackages.class, IS_UPDATABLE);
    }

    public PluginPackage(EntityRef owner, String title) {
        super(owner, PACKAGE, title, null);

        // Properties
        model.addDynamicProp(PROP_STATUS, new AnyType(), null, () -> new Iconified() {
            private PackageStatus status = PackageStatus.getStatusFor(PluginPackage.this);

            @Override
            public ImageIcon getIcon() {
                return status.getIcon();
            }
            @Override
            public String toString() {
                return status.getDescription(PluginPackage.this);
            }
        });

        // Editor settings
        ((AnyTypeView) model.getEditor(PROP_STATUS)).addCommand(new EditorCommand<AnyType, Object>(
                ICON_INFO,
                Language.get(Versioning.class,"history@command"),
                holder -> IComplexType.coalesce(getRemote(), getLocal()) != null
        ) {
            @Override
            public void execute(PropertyHolder<AnyType, Object> context) {
                VersionsDocument history = IComplexType.coalesce(getRemote(), getLocal()).getHistory();
                if (history != null) new Versioning.ShowChanges(history).execute(context);
            }
            @Override
            public boolean disableWithContext() {
                return false;
            }
        });
        getEditorPage().add(
                singlePluginView,
                new GridBagConstraints() {{
                    insets = new Insets(5, 0, 0, 0);
                    fill = GridBagConstraints.HORIZONTAL;
                    gridwidth = 2;
                    gridx = 0;
                    gridy = (getEditorPage().getComponentCount() - 2) / 2 + 1;
                }}
        );
    }

    @Override
    public final Class<? extends Entity> getChildClass() {
        return getPlugins().size() > 1 ? Plugin.class : null;
    }

    @Override
    public final boolean allowModifyChild() {
        return false;
    }

    @Override
    public final void loadChildren() {}

    @Override
    public final boolean isLeaf() {
        return true;
    }

    final IPluginRegistry.PackageDescriptor getLocal() {
        return local;
    }

    final IPluginRegistry.PackageDescriptor getRemote() {
        return remote;
    }

    final void setLocalDescriptor(IPluginRegistry.PackageDescriptor local) {
        boolean update = (
                (this.local != null && local != null && !local.getVersion().getNumber().equals(this.local.getVersion().getNumber())) ||
                (this.local != null && local == null) ||
                (this.local == null && local != null)
        );
        if (this.local != null) {
            //Drop listener
            this.local.getPlugins().forEach(pluginHandler -> pluginHandler.getView().model.removeChangeListener(updater));
        }
        this.local = local;
        if (this.local != null) {
            // Assign listener
            this.local.getPlugins().forEach(pluginHandler -> pluginHandler.getView().model.addChangeListener(updater));
        }

        try {
            if (local != null && getID() == null) {
                model.create(false);
            } else if (local == null && getID() != null) {
                ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).removeClassInstance(
                        PluginPackage.class,
                        getID()
                );
                setID(null);
            }
        } catch (Exception ignore) {}
        if (update) SwingUtilities.invokeLater(() -> onUpdateDescriptor(true));
    }

    final void setRemoteDescriptor(IPluginRegistry.PackageDescriptor remote) {
        boolean update = (
                (this.remote != null && remote != null && !remote.getVersion().getNumber().equals(this.remote.getVersion().getNumber())) ||
                (this.remote != null && remote == null) ||
                (this.remote == null && remote != null)
        );
        this.remote = remote;
        if (update) SwingUtilities.invokeLater(() -> onUpdateDescriptor(false));
    }

    private void onUpdateDescriptor(boolean local) {
        if (IComplexType.coalesce(this.local, this.remote) == null) {
            return;
        }

        setIcon(getPackageIcon());
        model.updateDynamicProps(PROP_STATUS);
        setMode(MODE_SELECTABLE + (isLoaded() ? MODE_ENABLED : MODE_NONE));

        IPluginRegistry.PackageDescriptor      loadFrom = IComplexType.coalesce(getLocal(), getRemote());
        List<PluginHandler<? extends IPlugin>> plugins = loadFrom == null ? Collections.emptyList() : loadFrom.getPlugins();

        if (!(!local && getLocal() != null)) {
            this.childrenList().forEach(this::detach);
            plugins.forEach(pluginHandler -> attach(pluginHandler.getView()));
        }

        if (loadFrom != null) {
            singlePluginView.removeAll();
            singlePluginView.setVisible(plugins.size() == 1);
            if (plugins.size() == 1) {
                singlePluginView.add(plugins.get(0).getView().getEditorPage(), BorderLayout.NORTH);
            }
        }
    }

    final String getPackageId() {
        final IPluginRegistry.PackageDescriptor descriptor = IComplexType.coalesce(local, remote);
        return descriptor != null ? descriptor.getId() : null;
    }

    final boolean isLoaded() {
        return local != null;
    }

    List<PluginHandler<? extends IPlugin>> getPlugins() {
        final IPluginRegistry.PackageDescriptor descriptor = IComplexType.coalesce(local, remote);
        return descriptor != null ? descriptor.getPlugins() : Collections.emptyList();
    }

    private ImageIcon getPackageIcon() {
        final ImageIcon defIcon = getPlugins().size() == 1 ? getPlugins().get(0).getIcon() : PACKAGE;
        return PackageStatus.getStatusFor(PluginPackage.this) == PackageStatus.Deprecated ? ImageUtils.combine(
                defIcon,
                ImageUtils.resize(DEPRECATED, .7f),
                SwingConstants.SOUTH_EAST
        ) : defIcon;
    }


    public enum PackageStatus implements Iconified {
        Development(
                DEVELOPMENT,
                pluginPackage ->
                        pluginPackage.local != null &&
                        pluginPackage.local.inDevelopment()
        ),
        Deprecated(
                DEPRECATED,
                pluginPackage ->
                        pluginPackage.local  != null &&
                        pluginPackage.remote != null &&
                        pluginPackage.remote.isNewerThan(pluginPackage.local) &&
                        pluginPackage.local.compatibleWith() != null && (
                                pluginPackage.local.compatibleWith().getNumber() == null ||
                                UpgradeService.VER_COMPARATOR.compare(
                                        pluginPackage.remote.compatibleWith(),
                                        pluginPackage.local.compatibleWith()
                                ) > 0
                        )
        ),
        Outdated(
                OUTDATED,
                pluginPackage ->
                        pluginPackage.local  != null &&
                        pluginPackage.remote != null &&
                        pluginPackage.remote.isNewerThan(pluginPackage.local)
        ),
        Present(
                PRESENT,
                pluginPackage -> pluginPackage.local != null
        ),
        Absent(
                ABSENT,
                pluginPackage -> pluginPackage.local == null
        );

        private final ImageIcon icon;
        private final String    desc;
        private final Predicate<PluginPackage> test;

        static PackageStatus getStatusFor(PluginPackage pluginPackage) {
            for (PackageStatus status : PackageStatus.values()) {
                if (status.test.test(pluginPackage)) {
                    return status;
                }
            }
            return Absent;
        }

        PackageStatus(ImageIcon icon, Predicate<PluginPackage> suitFunction) {
            this.icon = icon;
            this.desc = Language.get(PluginPackage.class, "status@".concat(name().toLowerCase()));
            this.test = suitFunction;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        private String getDescription(PluginPackage pluginPackage) {
            return MessageFormat.format(
                    desc,
                    pluginPackage.local  != null ? pluginPackage.local.getVersion().getNumber() : null,
                    pluginPackage.remote != null ? pluginPackage.remote.getVersion().getNumber() : null
            );
        }
    }


    static class EditOptions extends EntityCommand<PluginPackage> {

        public EditOptions() {
            super(
                    "edit options",
                    Language.get(Plugin.class, "options@title"),
                    Plugin.ICON_OPTIONS,
                    Language.get(Plugin.class, "options@title"),
                    null
            );
            Function<List<PluginPackage>, CommandStatus> defaultActivator = activator;
            activator = entities -> {
                boolean hasInvalidProp = entities.stream().anyMatch(pluginPackage -> !pluginPackage.getPlugins().get(0).getView().isOptionsValid());
                return new CommandStatus(
                        defaultActivator.apply(entities).isActive(),
                        hasInvalidProp ? ImageUtils.combine(getIcon(), ICON_WARN, SwingConstants.SOUTH_EAST) : getIcon()
                );
            };
        }

        @Override
        public void execute(PluginPackage context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPlugins().get(0).getView();
            plugin.getCommand(Plugin.EditOptions.class).execute(plugin, Collections.emptyMap());
            context.getCommand(EditOptions.class).activate();
        }
    }


    static class LoadPlugin extends EntityCommand<PluginPackage> {

        public LoadPlugin() {
            super("load plugin",
                    Language.get(Plugin.class, "load@title"),
                    ImageUtils.getByPath("/images/start.png"),
                    Language.get(Plugin.class, "load@title"),
                    pluginPackage -> {
                        final List<PluginHandler<? extends IPlugin>> plugins = pluginPackage.getPlugins();
                        return !plugins.isEmpty() && plugins.get(0).getView().loadAllowed();
                    }
            );
        }

        @Override
        public final void execute(PluginPackage context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPlugins().get(0).getView();
            plugin.getCommand(Plugin.LoadPlugin.class).execute(plugin, Collections.emptyMap());
        }
    }


    static class UnloadPlugin extends EntityCommand<PluginPackage> {

        public UnloadPlugin() {
            super("unload plugin",
                    Language.get(Plugin.class, "unload@title"),
                    ImageUtils.getByPath("/images/stop.png"),
                    Language.get(Plugin.class, "unload@title"),
                    pluginPackage -> {
                        final List<PluginHandler<? extends IPlugin>> plugins = pluginPackage.getPlugins();
                        return !plugins.isEmpty() && plugins.get(0).getView().isEnabled();
                    }
            );
        }

        @Override
        public final void execute(PluginPackage context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPlugins().get(0).getView();
            plugin.getCommand(Plugin.UnloadPlugin.class).execute(plugin, Collections.emptyMap());
        }
    }

}
