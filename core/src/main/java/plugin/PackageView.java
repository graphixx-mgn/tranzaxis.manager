package plugin;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.instance.InstanceCommunicationService;
import codex.log.Logger;
import codex.model.*;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PackageView extends Catalog {

    private final static InstanceCommunicationService ICS = (InstanceCommunicationService) ServiceRegistry.getInstance().lookupService(InstanceCommunicationService.class);

    final static ImageIcon PACKAGE   = ImageUtils.getByPath("/images/repository.png");
    final static ImageIcon DISABLED  = ImageUtils.getByPath("/images/unavailable.png");
    final static ImageIcon PUBLISHED = ImageUtils.getByPath("/images/plugin_public.png");
    final static ImageIcon BUILDING  = ImageUtils.getByPath("/images/warn.png");
    final static ImageIcon READY     = ImageUtils.getByPath("/images/success.png");

    private final static String PROP_VERSION = "version";
    private final static String PROP_AUTHOR  = "author";
    private final static String PROP_PUBLIC  = "public";

    private final Supplier<List<PluginHandler>> pluginsSupplier;
    private final Supplier<PluginPackage>       packageSupplier;
    private final INodeListener updatePackage = new INodeListener() {
        @Override
        public void childChanged(INode node) {
            setIcon(getStatusIcon());
        }
    };

    static {
        CommandRegistry.getInstance().registerCommand(DeletePackage.class);
        CommandRegistry.getInstance().registerCommand(LoadPackage.class);
        CommandRegistry.getInstance().registerCommand(UnloadPackage.class);
        CommandRegistry.getInstance().registerCommand(PublishPackage.class);
    }

    private PackageView(EntityRef owner, String title) {
        this(null);
    }

    PackageView(PluginPackage pluginPackage) {
        super(
                null,
                null,
                pluginPackage == null ? null : pluginPackage.getTitle(),
                null
        );
        pluginsSupplier = pluginPackage == null ? ArrayList::new : pluginPackage::getPlugins;
        packageSupplier = pluginPackage == null ? null : () -> pluginPackage;

        model.addDynamicProp(PROP_VERSION, new AnyType(), null, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return pluginPackage == null || !pluginPackage.isBuild() ? READY : BUILDING;
            }

            @Override
            public String toString() {
                return pluginPackage == null ? null : (
                            pluginPackage.isBuild() ? MessageFormat.format(
                                    Language.get(PackageView.class, "version.build"),
                                    pluginPackage.getVersion()
                            ) : pluginPackage.getVersion()
                );
            }
        });
        model.addDynamicProp(PROP_AUTHOR,  new Str(null), null, pluginPackage == null ? null : pluginPackage::getAuthor);

        model.addUserProp(PROP_PUBLIC,  new Bool(false), false, Access.Edit);

        if (pluginPackage != null) {
            if (pluginPackage.size() == 1) {
                PluginHandler pluginHandler = pluginPackage.getPlugins().get(0);
                Plugin pluginView = pluginHandler.getView();

                pluginView.addNodeListener(updatePackage);

                pluginView.model.getProperties(Access.Edit).forEach(propName -> {
                    if (!EntityModel.SYSPROPS.contains(propName)) {
                        model.addDynamicProp(
                                propName,
                                pluginView.model.getProperty(propName).getPropValue(),
                                Access.Select,
                                () -> pluginView.model.getValue(propName)
                        );
                        changePropertyNaming(
                                model.getProperty(propName),
                                pluginView.model.getProperty(propName).getTitle(),
                                pluginView.model.getProperty(propName).getDescriprion()
                        );
                    }
                });
                model.addPropertyGroup(
                        Language.get("type@group"),
                        pluginView.model.getProperties(Access.Edit).toArray(new String[]{})
                );
            } else {
                pluginPackage.getPlugins().forEach(pluginHandler -> {
                    Plugin pluginView = pluginHandler.getView();
                    pluginView.addNodeListener(updatePackage);
                    insert(pluginView);
                });
            }
            if (packageSupplier.get().isBuild()) {
                try {
                    setPublished(false);
                } catch (Exception e) {/**/}
            }
        }
        setIcon(getStatusIcon());
    }

    void setPublished(boolean published) throws Exception {
        model.setValue(PROP_PUBLIC, published);
        model.commit(true);
        ICS.getInstances().forEach(instance -> {
            try {
                final IPluginLoaderService pluginLoader = (IPluginLoaderService) instance.getService(PluginLoaderService.class);
                pluginLoader.packagePublicationChanged(
                        new IPluginLoaderService.RemotePackage(packageSupplier.get()),
                        isPublished()
                );
            } catch (RemoteException | NotBoundException e) {
                //
            }
        });
    }

    boolean isPublished() {
        return model.getValue(PROP_PUBLIC) == Boolean.TRUE;
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return pluginsSupplier.get().size() > 1 ? Plugin.class : null;
    }

    @Override
    protected Collection<String> getChildrenPIDs() {
        return Collections.emptyList();
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    private ImageIcon getStatusIcon() {
        if (pluginsSupplier.get().size() == 1) {
            return pluginsSupplier.get().get(0).getView().getStatusIcon();
        } else {
            boolean hasLoaded = pluginsSupplier.get().stream().anyMatch(pluginHandler -> pluginHandler.getView().isEnabled());
            return  hasLoaded ? PACKAGE : ImageUtils.combine(ImageUtils.grayscale(PACKAGE), DISABLED);
        }
    }

    static void changePropertyNaming(PropertyHolder propHolder, String title, String desc) {
        try {
            Field fieldTitle = PropertyHolder.class.getDeclaredField("title");
            Field fieldDesc  = PropertyHolder.class.getDeclaredField("desc");

            fieldTitle.setAccessible(true);
            fieldDesc.setAccessible(true);

            fieldTitle.set(propHolder, title);
            fieldDesc.set(propHolder, desc);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            //
        }
    }


    class LoadPackage extends EntityCommand<PackageView> {
        LoadPackage() {
            super(
                    "load package",
                    Language.get(PackageView.class, "load@title"),
                    ImageUtils.getByPath("/images/plugin_load.png"),
                    Language.get(PackageView.class, "load@title"),
                    packageView -> packageView.pluginsSupplier.get().stream().anyMatch(plugin -> !plugin.getView().isEnabled())
            );
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            Map<String, String> errors = new HashMap<>();

            context.pluginsSupplier.get().stream()
                    .filter(pluginHandler  -> !pluginHandler.getView().isEnabled())
                    .forEach(pluginHandler -> {
                        try {
                            pluginHandler.loadPlugin();
                            pluginHandler.getView().setEnabled(true, true);
                        } catch (PluginException e) {
                            String pluginId = Plugin.getId(pluginHandler);
                            Logger.getLogger().warn("Unable to load plugin ''{0}''\n{1}", pluginId, Logger.stackTraceToString(e));
                            errors.put(pluginId, e.getMessage());
                        }
                    });
            if (!errors.isEmpty()) {
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(PackageView.class, "load@error"),
                                errors.entrySet().stream()
                                        .map(entry -> MessageFormat.format("<p>&bull;&nbsp;<b>{0}</b>: {1}<br>", entry.getKey(), entry.getValue()))
                                        .collect(Collectors.joining())
                        )
                );
            }
            context.setIcon(context.getStatusIcon());
        }
    }


    class UnloadPackage extends EntityCommand<PackageView> {
        UnloadPackage() {
            super(
                    "unload package",
                    Language.get(PackageView.class, "unload@title"),
                    ImageUtils.getByPath("/images/plugin_unload.png"),
                    Language.get(PackageView.class, "unload@title"),
                    packageView -> packageView.pluginsSupplier.get().stream().anyMatch(plugin -> plugin.getView().isEnabled())
            );
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            Map<String, String> errors = new HashMap<>();

            context.pluginsSupplier.get().stream()
                    .filter(pluginHandler  -> pluginHandler.getView().isEnabled())
                    .forEach(pluginHandler -> {
                        try {
                            pluginHandler.unloadPlugin();
                            pluginHandler.getView().setEnabled(false, true);
                        } catch (PluginException e) {
                            String pluginId = Plugin.getId(pluginHandler);
                            Logger.getLogger().warn("Unable to unload plugin ''{0}''\n{1}", pluginId, Logger.stackTraceToString(e));
                            errors.put(pluginId, e.getMessage());
                        }
                    });
            if (!errors.isEmpty()) {
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(PackageView.class, "unload@error"),
                                errors.entrySet().stream()
                                        .map(entry -> MessageFormat.format("<p>&bull;&nbsp;<b>{0}</b>: {1}<br>", entry.getKey(), entry.getValue()))
                                        .collect(Collectors.joining())
                        )
                );
            }
            context.setIcon(context.getStatusIcon());
        }
    }


    class DeletePackage extends EntityCommand<PackageView> {

        DeletePackage() {
            super(
                    "delete package",
                    Language.get(PackageView.class, "delete@title"),
                    ImageUtils.getByPath("/images/minus.png"),
                    Language.get(PackageView.class, "delete@title"),
                    null
            );
        }

        @Override
        public String acquireConfirmation() {
            String message;
            if (getContext().size() == 1) {
                message = MessageFormat.format(
                        Language.get(PackageView.class, "confirm@del.single"),
                        getContext().get(0)
                );
            } else {
                message = MessageFormat.format(
                        Language.get(PackageView.class, "confirm@del.range"),
                        getContext().stream()
                                .map(packageView -> "&bull;&nbsp;<b>"+packageView+"</b>")
                                .collect(Collectors.joining("<br>"))
                );
            }
            return message;
        }

        @Override
        public boolean multiContextAllowed() {
            return true;
        }

        @Override
        public void execute(PackageView context, Map<String, IComplexType> params) {
            try {
                PluginManager.getInstance().getPluginLoader().removePluginPackage(context.packageSupplier.get(), true);
                context.getParent().delete(context);
                for (PluginHandler pluginHandler : context.pluginsSupplier.get()) {
                    context.delete(pluginHandler.getView());
                }
            } catch (PluginException | IOException e) {
                Logger.getLogger().warn("Unable to remove plugin package ''{0}'': {1}", context.packageSupplier.get(), e.getMessage());
                MessageBox.show(
                        MessageType.WARNING,
                        MessageFormat.format(
                                Language.get(PackageView.class, "delete@error"),
                                e.getMessage()
                        )
                );
                context.setIcon(context.getStatusIcon());
            }
        }
    }


    class PublishPackage extends EntityCommand<PackageView> {

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
                    return new CommandStatus(
                            !packages.get(0).packageSupplier.get().isBuild(),
                            packages.get(0).isPublished() ? PUBLISHED : ImageUtils.combine(ImageUtils.grayscale(PUBLISHED), DISABLED)
                    );
                }
            };
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
                MessageBox.show(MessageType.ERROR, e.getMessage());
            }
        }
    }
}
