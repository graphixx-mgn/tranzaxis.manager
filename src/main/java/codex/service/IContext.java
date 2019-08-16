package codex.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface IContext {

    default ContextPresentation getClassPresentation() {
        return new ContextPresentation(this.getClass());
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Definition {
        String icon();
        String title();
    }

}
