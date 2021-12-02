package plugin;

import codex.context.IContext;
import codex.context.RootContext;
import codex.log.Logger;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Supplier;

public abstract class PluginHandler<P extends IPlugin> implements Iconified {

    final Class<P>          pluginClass;
    private final Plugin<P> pluginConfig;
    private final String    packageId;

    private static <P extends IPlugin>  List<Class<? extends IContext>> getContexts(Class<P> pluginClass) {
        List<Class<? extends IContext>> contexts = new LinkedList<>();
        contexts.add(pluginClass);
        ClassIndex.getSubclasses(IContext.class, pluginClass.getClassLoader()).forEach(subContext -> {
            Class<? extends IContext> parentCtx = subContext;
            while (true) {
                IContext.Definition ctxDef = parentCtx.getAnnotation(IContext.Definition.class);
                if (ctxDef == null || ctxDef.parent() == RootContext.class) break;
                if (ctxDef.parent() == pluginClass) {
                    contexts.add(subContext);
                    break;
                }
                parentCtx = ctxDef.parent();
            }
        });
        return contexts;
    }

    protected PluginHandler(Class<P> pluginClass, String pkgId) {
        this.pluginClass = pluginClass;
        this.packageId   = pkgId;
        pluginConfig     = new Plugin<>(this);
    }

    String getPluginId() {
        return MessageFormat.format("{0}/{1}", packageId, pluginClass.getCanonicalName().toLowerCase());
    }

    Plugin<P> getView() {
        return pluginConfig;
    }

    public Class<P> getPluginClass() {
        return pluginClass;
    }

    protected abstract String    getTitle();
    protected abstract Iconified getDescription();
    protected Map<String, Supplier<Iconified>> getTypeDefinition() {
        return Collections.emptyMap();
    }

    protected boolean loadPlugin() throws PluginException {
        getContexts(pluginClass).forEach(aClass -> Logger.getContextRegistry().registerContext(aClass));
        return true;
    }

    protected boolean unloadPlugin() throws PluginException {
        getContexts(pluginClass).forEach(aClass -> Logger.getContextRegistry().unregisterContext(aClass));
        return true;
    }

    protected boolean reloadPlugin(PluginHandler<P> pluginHandler) throws PluginException {
        try {
            if (unloadPlugin()) {
                pluginHandler.loadPlugin();
            }
        } catch (Exception e) {
            throw new PluginException(e.getMessage());
        }
        return true;
    }

    @Override
    public final ImageIcon getIcon() {
        return ImageUtils.getByPath(pluginClass, Language.get(pluginClass, "icon"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginHandler<?> that = (PluginHandler<?>) o;
        return pluginClass.getTypeName().equals(that.pluginClass.getTypeName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginClass.getTypeName());
    }

    @Override
    public final String toString() {
        return Language.get(pluginClass, "title");
    }
}
