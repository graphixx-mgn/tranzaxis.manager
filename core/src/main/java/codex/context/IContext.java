package codex.context;

import org.atteo.classindex.IndexSubclasses;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@IndexSubclasses
public interface IContext {

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Definition {
        String id();
        String name();
        String icon() default "";
        Class<? extends IContext> parent() default RootContext.class;
    }

    interface IContextProvider {
        Definition getDefinition(Class<? extends IContext> contextClass);
    }

    final class DefaultContextProvider implements IContextProvider {
        public Definition getDefinition(Class<? extends IContext> contextClass) {
            return contextClass.getAnnotation(Definition.class);
        }
    }
}
