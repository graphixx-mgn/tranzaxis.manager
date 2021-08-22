package plugin.command;

import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.*;
import javax.swing.*;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

final class CommandPluginHandler<V extends Entity> extends PluginHandler<CommandPlugin<V>> {

    private final static String PROP_ENTITY  = "class";
    private final static String PROP_COMMAND = "command";

    private final Iconified typeDescription = new Iconified() {
        @Override
        public ImageIcon getIcon() {
            return CommandPlugin.COMMAND_ICON;
        }
        @Override
        public String toString() {
            return Language.get(CommandPluginHandler.class, "type");
        }
    };

    CommandPluginHandler(Class<CommandPlugin<V>> pluginClass, String pkgId) {
        super(pluginClass, pkgId);
    }

    @Override
    protected Map<String, Supplier<Iconified>> getTypeDefinition() {
        return new LinkedHashMap<String, Supplier<Iconified>>() {{
            put(PROP_ENTITY, () -> new Iconified() {
                private Entity entity = Entity.newPrototype(getEntityClass());

                @Override
                public ImageIcon getIcon() {
                    return entity.getIcon();
                }
                @Override
                public String toString() {
                    return entity.getClass().getSimpleName();
                }
            });
            put(PROP_COMMAND, () -> new Iconified() {
                @Override
                public ImageIcon getIcon() {
                    return ImageUtils.getByPath(getPluginClass(), Language.get(getPluginClass(), "icon"));
                }
                @Override
                public String toString() {
                    return getTitle();
                }
            });
        }};
    }

    @Override
    protected final boolean loadPlugin() throws PluginException {
        super.loadPlugin();
        CommandRegistry.getInstance().registerCommand(getEntityClass(), getPluginClass());
        return true;
    }

    @Override
    protected final boolean unloadPlugin() throws PluginException {
        CommandRegistry.getInstance().getRegisteredCommands(getEntityClass()).stream()
                .filter(command -> command.getClass().equals(getPluginClass()))
                .findFirst()
                .ifPresent(command -> CommandRegistry.getInstance().unregisterCommand(getEntityClass(), getPluginClass()));
        super.unloadPlugin();
        return true;
    }

    @Override
    protected boolean reloadPlugin(PluginHandler<CommandPlugin<V>> pluginHandler) throws PluginException {
        super.reloadPlugin(pluginHandler);
        return true;
    }

    @Override
    protected final String getTitle() {
        return Language.get(getPluginClass(), "title");
    }

    @Override
    protected final Iconified getDescription() {
        return typeDescription;
    }

    @SuppressWarnings("unchecked")
    private Class<V> getEntityClass() {
        return (Class<V>) ((ParameterizedType) getPluginClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

}
