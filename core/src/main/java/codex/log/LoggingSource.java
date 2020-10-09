package codex.log;

import codex.context.IContext;
import org.atteo.classindex.IndexAnnotated;
import java.lang.annotation.*;

@Inherited
@IndexAnnotated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoggingSource {
    boolean debugOption() default false;
    Level   defaultLevel() default Level.Info;
    Class<? extends IContext.IContextProvider> ctxProvider() default IContext.DefaultContextProvider.class;
}