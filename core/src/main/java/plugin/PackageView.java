package plugin;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.log.Logger;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PackageView extends Catalog {

    final static ImageIcon PACKAGE  = ImageUtils.getByPath("/images/repository.png");
    final static ImageIcon DISABLED = ImageUtils.getByPath("/images/unavailable.png");

    private final static String PROP_TITLE   = "title";
    private final static String PROP_VERSION = "version";
    private final static String PROP_AUTHOR  = "author";
    private final static String PROP_DESC    = "desc";

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

        model.addDynamicProp(PROP_TITLE,   new Str(null), null, () -> pluginPackage == null ? null : pluginPackage.getId());
        model.addDynamicProp(PROP_VERSION, new Str(null), null, () -> pluginPackage == null ? null : pluginPackage.getVersion());
        model.addDynamicProp(PROP_AUTHOR,  new Str(null), null, pluginPackage == null ? null : pluginPackage::getAuthor);

        if (pluginPackage != null) {
            if (pluginPackage.size() == 1) {
                PluginHandler pluginHandler = pluginPackage.getPlugins().get(0);
                Plugin pluginView = pluginHandler.getView();

                pluginView.addNodeListener(updatePackage);

                pluginView.model.getProperties(Access.Edit).forEach(propName -> {
                    model.addDynamicProp(
                            propName,
                            pluginView.model.getProperty(propName).getPropValue(),
                            Access.Select,
                            () -> pluginView.model.getValue(propName)
                    );
                    changePropertyNaming(
                            propName,
                            pluginView.model.getProperty(propName).getTitle(),
                            pluginView.model.getProperty(propName).getDescriprion()
                    );
                });

                model.addDynamicProp(PROP_DESC, new AnyType() {
                    @Override
                    public IEditorFactory editorFactory() {
                        return TextView::new;
                    }
                }, Access.Select, () -> Language.get(pluginHandler.pluginClass, "desc"));

                model.addPropertyGroup(
                        Language.get("type@group"),
                        pluginView.model.getProperties(Access.Edit).toArray(new String[]{})
                );
                model.addPropertyGroup(Language.get("type@group"), PROP_DESC);
            } else {
                pluginPackage.getPlugins().forEach(pluginHandler -> {
                    Plugin pluginView = pluginHandler.getView();
                    pluginView.addNodeListener(updatePackage);
                    insert(pluginView);
                });
            }
        }
        setIcon(getStatusIcon());
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

    private void changePropertyNaming(String propName, String title, String desc) {
        PropertyHolder propHolder = model.getProperty(propName);
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
}
