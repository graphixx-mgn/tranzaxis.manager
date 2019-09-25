package codex.log;

import org.atteo.classindex.IndexAnnotated;
import java.lang.annotation.*;

@Inherited
@IndexAnnotated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoggingSource {
    boolean debugOption() default false;
}