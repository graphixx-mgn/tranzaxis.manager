package plugin.command;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.utils.Caller;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.IPlugin;
import plugin.Pluggable;
import javax.swing.*;
import java.util.function.Predicate;

@Pluggable(pluginHandlerClass = CommandPluginHandler.class)
@EntityCommand.Definition(parentCommand = CommandLauncher.class)
public abstract class CommandPlugin<V extends Entity> extends EntityCommand<V> implements IPlugin {

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
                null, available
        );
    }

}
