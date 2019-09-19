package plugin;

import codex.type.Iconified;
import java.util.LinkedList;
import java.util.List;

public abstract class PluginHandler<P extends IPlugin> implements Iconified {

    private   final List<IHandlerListener> listeners = new LinkedList<>();
    protected final Class<P> pluginClass;

    protected PluginHandler(Class<P> pluginClass) {
        this.pluginClass = pluginClass;
    }

    protected abstract Plugin    getView();
    protected abstract String    getTitle();
    protected abstract String    getDescription();
    protected abstract Iconified getTypeDefinition();

    synchronized void addHandlerListener(IHandlerListener listener) {
        listeners.add(listener);
    }

    synchronized void removeHandlerListener(IHandlerListener listener) {
        listeners.remove(listener);
    }

    protected void loadPlugin() throws PluginException {
        new LinkedList<>(listeners).forEach(listener -> listener.pluginLoaded(this));
    }
    protected void unloadPlugin() throws PluginException {
        new LinkedList<>(listeners).forEach(listener -> listener.pluginUnloaded(this));
    }

    interface IHandlerListener {
        void pluginLoaded(PluginHandler handler);
        void pluginUnloaded(PluginHandler handler);
    }
}
