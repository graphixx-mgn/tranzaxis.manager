package plugin.command;

import codex.command.EntityCommand;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.launcher.Shortcut;
import codex.log.Logger;
import codex.model.Access;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.ServiceRegistry;
import codex.type.AnyType;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.*;
import javax.swing.*;
import java.lang.reflect.ParameterizedType;
import java.text.MessageFormat;
import java.util.function.Supplier;

class CommandPluginHandler<V extends Entity> extends PluginHandler<CommandPlugin<V>> {

    private final static CommandRegistry COMMAND_REGISTRY = CommandRegistry.getInstance();

    private final static String PROP_ENTITY  = "class";
    private final static String PROP_COMMAND = "command";
    private final static String PROP_DESC    = "desc";

    private final Plugin pluginConfig = new Plugin(this);
    private final Class<V>     entityClass = getEntityClass();
    private final Iconified    typeDefinition = new Iconified() {
        @Override
        public ImageIcon getIcon() {
            return ImageUtils.getByPath(pluginClass, Language.get(pluginClass, "icon"));
        }
        @Override
        public String toString() {
            return Language.get(pluginClass, "title");
        }
    };

    private final Supplier<EntityCommand> launcherSupplier = () -> COMMAND_REGISTRY.getRegisteredCommands(entityClass).stream()
            .filter(command -> command.getClass().equals(CommandLauncher.class))
            .findFirst().orElseGet(() -> COMMAND_REGISTRY.registerCommand(entityClass, CommandLauncher.class));

    {
        // Inject plugin type properties
        pluginConfig.model.addDynamicProp(PROP_ENTITY, new AnyType(), Access.Select, () -> new Iconified() {
            private Entity entity = Entity.newPrototype(entityClass);

            @Override
            public ImageIcon getIcon() {
                return entity.getIcon();
            }

            @Override
            public String toString() {
                return entity.getClass().getSimpleName();
            }
        });
        pluginConfig.model.addDynamicProp(PROP_COMMAND, new AnyType(), Access.Select, () -> typeDefinition);
        pluginConfig.model.addDynamicProp(PROP_DESC, new AnyType() {
            @Override
            public IEditorFactory editorFactory() {
                return TextView::new;
            }
        }, Access.Select, () -> Language.get(pluginClass, PROP_DESC));
    }


    CommandPluginHandler(Class<CommandPlugin<V>> pluginClass) {
        super(pluginClass);
        launcherSupplier.get().activate();
    }

    @Override
    protected final void loadPlugin() throws PluginException {
        EntityCommand<? extends Entity> command = CommandRegistry.getInstance().registerCommand(entityClass, pluginClass);
        launcherSupplier.get().activate();
        updateShortcuts(entityClass, command.getName());
        Logger.getLogger().debug("PXE: Enabled plugin: {0}", getDescription());
    }

    @Override
    protected final void unloadPlugin() throws PluginException {
        COMMAND_REGISTRY.getRegisteredCommands(entityClass).stream()
                .filter(command -> command.getClass().equals(pluginClass))
                .findFirst()
                .ifPresent(command -> {
                    CommandRegistry.getInstance().unregisterCommand(entityClass, pluginClass);
                    launcherSupplier.get().activate();
                    updateShortcuts(entityClass, command.getName());
                    Logger.getLogger().debug("PXE: Disabled plugin: {0}", getDescription());
                });

    }

    private static <V extends Entity> void updateShortcuts(Class<V> entityClass, String commandName) {
        IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
        CAS.readCatalogEntries(null, Shortcut.class).keySet().stream()
                .map(id -> CAS.readClassInstance(Shortcut.class, id))
                .filter(properties -> properties.get(Shortcut.PROP_COMMAND).equals(commandName))
                .forEach(properties -> {
                    ((Shortcut) EntityRef.build(Shortcut.class, properties.get(EntityModel.ID)).getValue()).update();
                });
    }

    @Override
    protected final Plugin getView() {
        return pluginConfig;
    }

    @Override
    protected final String getTitle() {
        return Language.get(pluginClass, "title");
    }

    @Override
    protected final String getDescription() {
        return MessageFormat.format(
                "Type: {0} [{1}] / Command: {2}",
                CommandPlugin.class.getSimpleName(),
                entityClass.getSimpleName(),
                getTitle()
        );
    }

    @Override
    protected final Iconified getTypeDefinition() {
        return typeDefinition;
    }

    @SuppressWarnings("unchecked")
    private Class<V> getEntityClass() {
        return (Class<V>) ((ParameterizedType) pluginClass.getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @Override
    public final ImageIcon getIcon() {
        return CommandPlugin.COMMAND_ICON;
    }

    @Override
    public final String toString() {
        return Language.get(CommandPlugin.class, "type");
    }

}
