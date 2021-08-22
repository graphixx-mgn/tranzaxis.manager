package plugin.command;

import codex.command.EntityCommand;
import codex.context.IContext;
import codex.log.LoggingSource;
import codex.model.Entity;
import codex.utils.Caller;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.IPlugin;
import plugin.Pluggable;
import plugin.PluginProvider;
import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.function.Predicate;

@Pluggable(pluginHandlerClass = CommandPluginHandler.class)
@LoggingSource(ctxProvider = CommandPlugin.ContextProvider.class)
public abstract class CommandPlugin<V extends Entity> extends EntityCommand<V> implements IPlugin {

    public final static class ContextProvider implements IContext.IContextProvider {

        @Override
        public IContext.Definition getDefinition(Class<? extends IContext> contextClass) {
            if (contextClass == MethodHandles.lookup().lookupClass().getEnclosingClass()) {
                return null;
            }
            return new IContext.Definition() {

                @Override
                public Class<? extends Annotation> annotationType() {
                    return IContext.Definition.class;
                }

                @Override
                public String id() {
                    return "EXT.Cmd";
                }

                @Override
                public String name() {
                    return Language.get(contextClass, "title", Language.DEF_LOCALE);
                }

                @Override
                public String icon() {
                    return Language.get(contextClass, "icon");
                }

                @Override
                public Class<? extends IContext> parent() {
                    return PluginProvider.class;
                }
            };
        }
    }

    final static ImageIcon COMMAND_ICON = ImageUtils.getByPath("/images/command.png");

    private static Class getPluginClass() {
        return new Caller().getClassStack().stream()
                .filter(aClass -> aClass != CommandPlugin.class)
                .findFirst()
                .orElse(CommandPlugin.class);
    }

    public CommandPlugin(Predicate<V> available) {
        super(
                null,
                Language.get(getPluginClass(), "title"),
                ImageUtils.getByPath(getPluginClass(), Language.get(getPluginClass(), "icon")),
                Language.get(getPluginClass(), "title"), available
        );
    }

}
