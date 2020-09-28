package plugin;

import codex.command.CommandStatus;
import codex.command.EditorCommand;
import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditorFactory;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.instance.IInstanceDispatcher;
import codex.log.Logger;
import codex.model.*;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.xml.VersionsDocument;
import javax.swing.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PackageView extends Catalog {

    private final static IInstanceDispatcher ICS = ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class);

    private final static ImageIcon ICON_INFO = ImageUtils.getByPath("/images/info.png");
    private final static ImageIcon ICON_WARN = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), .7f);

    final static ImageIcon PACKAGE = ImageUtils.getByPath("/images/repository.png");
    final static ImageIcon DISABLED = ImageUtils.getByPath("/images/unavailable.png");
    final static ImageIcon PUBLISHED = ImageUtils.getByPath("/images/plugin_public.png");
    final static ImageIcon BUILDING = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon READY = ImageUtils.getByPath("/images/success.png");

    private final static String PROP_VERSION = "version";
    private final static String PROP_AUTHOR = "author";
    private final static String PROP_PUBLIC = "public";

    private final PluginPackage pluginPackage;

    private final INodeListener updatePackage = new INodeListener() {
        @Override
        public void childChanged(INode node) {
            boolean hasLoaded = pluginPackage.getPlugins().stream().anyMatch(pluginHandler -> pluginHandler.getView().isEnabled());
            setMode(MODE_SELECTABLE + (hasLoaded ? MODE_ENABLED : MODE_NONE));
        }
    };

    static {
        CommandRegistry.getInstance().registerCommand(
                PackageView.class,
                PublishPackage.class,
                packageView -> !packageView.pluginPackage.inDevelopment()
        );
        CommandRegistry.getInstance().registerCommand(
                PackageView.class,
                EditOptions.class,
                packageView ->
                        packageView.getPackage().getPlugins().size() == 1 &&
                        packageView.getPackage().getPlugins().get(0).getView().hasOptions()
        );
        CommandRegistry.getInstance().registerCommand(
                PackageView.class,
                LoadPlugin.class,
                packageView -> packageView.getPackage().getPlugins().size() == 1
        );
        CommandRegistry.getInstance().registerCommand(
                PackageView.class,
                UnloadPlugin.class,
                packageView -> packageView.getPackage().getPlugins().size() == 1
        );
    }

    PackageView(PluginPackage pluginPackage) {
        super(
                null,
                null,
                pluginPackage == null ? null : pluginPackage.getTitle(),
                null
        );
        this.pluginPackage = pluginPackage;

        model.addDynamicProp(PROP_VERSION, new AnyType(), null, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return pluginPackage == null || !pluginPackage.inDevelopment() ? READY : BUILDING;
            }

            @Override
            public String toString() {
                return pluginPackage == null ? null : (
                            pluginPackage.inDevelopment() ? MessageFormat.format(
                                    Language.get(PackageView.class, "version.build"),
                                    pluginPackage.getVersion()
                            ) : pluginPackage.getVersion()
                );
            }
        });
        model.addDynamicProp(PROP_AUTHOR, new Str(null), null, pluginPackage == null ? null : pluginPackage::getAuthor);
        model.addUserProp(PROP_PUBLIC, new Bool(false), false, Access.Edit);

        if (pluginPackage != null) {
            //noinspection unchecked
            (model.getEditor(PROP_VERSION)).addCommand(new ShowChanges());

            if (pluginPackage.getPlugins().size() == 1) {
                PluginHandler pluginHandler = pluginPackage.getPlugins().get(0);
                Plugin pluginView = pluginHandler.getView();

                setIcon(pluginHandler.getIcon());
                pluginView.addNodeListener(updatePackage);

                pluginView.model.getProperties(Access.Edit).forEach(propName -> {
                    if (!EntityModel.SYSPROPS.contains(propName)) {
                        Class<?> propClass = Plugin.VIEW_PROPS.contains(propName) ? Plugin.class : pluginHandler.getClass();
                        model.addDynamicProp(
                                propName,
                                Language.get(propClass, propName + PropertyHolder.PROP_NAME_SUFFIX),
                                Language.get(propClass, propName + PropertyHolder.PROP_DESC_SUFFIX),
                                new AnyType() {
                                    @Override
                                    @SuppressWarnings("unchecked")
                                    public IEditorFactory<AnyType, Object> editorFactory() {
                                        return pluginView.model.getProperty(propName).getPropValue().editorFactory();
                                    }
                                },
                                Access.Select,
                                () -> pluginView.model.calculateDynamicValue(propName)
                        );
                    }
                });
                model.addPropertyGroup(
                        Language.get("type@group"),
                        pluginView.model.getProperties(Access.Edit).toArray(new String[]{})
                );

            } else {
                setIcon(PACKAGE);

                pluginPackage.getPlugins().forEach(pluginHandler -> {
                    Plugin pluginView = pluginHandler.getView();
                    pluginView.addNodeListener(updatePackage);
                    attach(pluginView);
                });
            }
            updatePackage.childChanged(null);
            if (getID() == null) {
                try {
                    model.create(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    PluginPackage getPackage() {
        return pluginPackage;
    }

    private void setPublished(boolean published) throws Exception {
        model.setValue(PROP_PUBLIC, published);
        model.commit(true);
        final IPluginLoaderService.RemotePackage remotePackage = new IPluginLoaderService.RemotePackage(getPackage());
        SwingUtilities.invokeLater(() -> ICS.getInstances().forEach(instance -> {
            try {
                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                pluginLoader.packagePublicationChanged(remotePackage, isPublished());
            } catch (RemoteException | NotBoundException e) {
                Throwable rootCause = PluginLoader.getCause(e);
                rootCause.printStackTrace();
                if (!(rootCause instanceof ClassNotFoundException))
                    Logger.getLogger().warn("Remote service call error: {0}", rootCause.getMessage());
            }
        }));
    }

    boolean isPublished() {
        return model.getValue(PROP_PUBLIC) == Boolean.TRUE && !pluginPackage.inDevelopment();
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return pluginPackage.getPlugins().size() > 1 ? Plugin.class : null;
    }

    @Override
    public void loadChildren() {
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }


    class ShowChanges extends EditorCommand<AnyType, Object> {
        ShowChanges() {
            super(
                    ICON_INFO,
                    Language.get(PluginManager.class, "history@command"),
                    holder -> getPackage().getChanges() != null
            );
        }

        @Override
        public void execute(PropertyHolder<AnyType, Object> context) {
            VersionsDocument verDoc = getPackage().getChanges();
            if (verDoc != null) {
                PluginManager.showVersionInfo(Arrays.asList(verDoc.getVersions().getVersionArray()));
            }
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }
    }


    static class LoadPlugin extends EntityCommand<PackageView> {

        public LoadPlugin() {
            super("load plugin",
                    Language.get(Plugin.class, "load@title"),
                    ImageUtils.getByPath("/images/start.png"),
                    Language.get(Plugin.class, "load@title"),
                    packageView -> packageView.getPackage().getPlugins().get(0).getView().loadAllowed()
            );
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPackage().getPlugins().get(0).getView();
            plugin.getCommand(Plugin.LoadPlugin.class).execute(plugin, Collections.emptyMap());
        }
    }


    static class UnloadPlugin extends EntityCommand<PackageView> {

        public UnloadPlugin() {
            super("unload plugin",
                    Language.get(Plugin.class, "unload@title"),
                    ImageUtils.getByPath("/images/stop.png"),
                    Language.get(Plugin.class, "unload@title"),
                    packageView -> packageView.getPackage().getPlugins().get(0).getView().isEnabled()
            );
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPackage().getPlugins().get(0).getView();
            plugin.getCommand(Plugin.UnloadPlugin.class).execute(plugin, Collections.emptyMap());
        }
    }


    static class EditOptions extends EntityCommand<PackageView> {

        public EditOptions() {
            super(
                    "edit options",
                    Language.get(Plugin.class, "options@title"),
                    Plugin.ICON_OPTIONS,
                    Language.get(Plugin.class, "options@title"),
                    null
            );
            Function<List<PackageView>, CommandStatus> defaultActivator = activator;
            activator = entities -> {
                boolean hasInvalidProp = entities.stream()
                        .anyMatch(packageView -> !packageView.getPackage().getPlugins().get(0).getView().isOptionsValid());
                return new CommandStatus(
                        defaultActivator.apply(entities).isActive(),
                        hasInvalidProp ? ImageUtils.combine(getIcon(), ICON_WARN, SwingConstants.SOUTH_EAST) : getIcon()
                );
            };
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            Plugin plugin = context.getPackage().getPlugins().get(0).getView();
            plugin.getCommand(Plugin.EditOptions.class).execute(plugin, Collections.emptyMap());
            context.getCommand(LoadPlugin.class).activate();
        }
    }


    static class PublishPackage extends EntityCommand<PackageView> {

        public PublishPackage() {
            super(
                    "publish package",
                    Language.get(PackageView.class, "publish@title"),
                    PACKAGE,
                    Language.get(PackageView.class, "publish@title"),
                    null
            );
            activator = packages -> {
                if (packages == null || packages.isEmpty() || packages.size() > 1) {
                    return new CommandStatus(false, ImageUtils.grayscale(PUBLISHED));
                } else {
                    if (packages.get(0).pluginPackage.inDevelopment()) {
                        return new CommandStatus(false, ImageUtils.grayscale(PUBLISHED));
                    } else {
                        return new CommandStatus(
                                true,
                                packages.get(0).isPublished() ? PUBLISHED : ImageUtils.combine(ImageUtils.grayscale(PUBLISHED), DISABLED)
                        );
                    }
                }
            };
        }

        @Override
        public Kind getKind() {
            return Kind.Admin;
        }

        @Override
        public String acquireConfirmation() {
            return MessageFormat.format(
                    Language.get(PackageView.class, getContext().get(0).isPublished() ? "confirm@unpublish" : "confirm@publish"),
                    getContext().get(0)
            );
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            try {
                context.setPublished(!context.isPublished());
            } catch (Exception e) {
                MessageBox.show(MessageType.WARNING, e.getMessage());
            }
        }
    }
}
