package plugin;


import codex.type.Iconified;

public abstract class PluginHandler<P extends IPlugin> implements Iconified {

    protected final Class<P> pluginClass;

    protected PluginHandler(Class<P> pluginClass) {
        this.pluginClass = pluginClass;
    }

    protected abstract void loadPlugin()   throws PluginException;
    protected abstract void unloadPlugin() throws PluginException;

    protected abstract Plugin    getView();
    protected abstract String    getTitle();
    protected abstract String    getDescription();
    protected abstract Iconified getTypeDefinition();

}
