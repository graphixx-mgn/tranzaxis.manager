package plugin;

import codex.context.IContext;
import codex.log.Logger;

@Pluggable()
public interface IPlugin extends IContext {

    default Object getOption(String optName) {
        try {
            return PluginManager.getOption(getClass(), optName);
        } catch (ClassNotFoundException e) {
            Logger.getContextLogger(getClass()).error("Option not found", e);
        }
        return null;
    }
}
