package plugin;

import org.atteo.classindex.IndexAnnotated;
import java.lang.annotation.*;

@Inherited
@IndexAnnotated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pluggable {
    Class<? extends PluginHandler> pluginHandlerClass();
}
