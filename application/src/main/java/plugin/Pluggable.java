package plugin;

import codex.model.ParamModel;
import org.atteo.classindex.IndexAnnotated;
import java.lang.annotation.*;

@Inherited
@IndexAnnotated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pluggable {
    Class<? extends PluginHandler> pluginHandlerClass() default PluginHandler.class;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface PluginOptions {
        Class<? extends OptionsProvider> provider();
    }

    abstract class OptionsProvider {
        public abstract ParamModel getOptions();
    }
}
