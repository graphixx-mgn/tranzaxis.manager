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
        String icon();
        String title();
    }
}
