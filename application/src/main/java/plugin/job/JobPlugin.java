package plugin.job;

import codex.context.IContext;
import codex.log.LoggingSource;
import codex.scheduler.AbstractJob;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import plugin.IPlugin;
import plugin.Pluggable;
import plugin.PluginLoader;
import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.Locale;

@Pluggable(pluginHandlerClass = JobPluginHandler.class)
@LoggingSource(ctxProvider = JobPlugin.ContextProvider.class)
public abstract class JobPlugin extends AbstractJob implements IPlugin {

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
                    return "EXT.Job";
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
                    return PluginLoader.class;
                }
            };
        }
    }

    final static ImageIcon JOB_ICON = ImageUtils.getByPath("/images/jobs.png");

    public JobPlugin(EntityRef owner, String title) {
        super(owner, title);
    }
}
