package codex.model;

import codex.type.EntityRef;
import org.atteo.classindex.IndexSubclasses;
import javax.swing.*;
import java.lang.annotation.*;

@IndexSubclasses
@ClassCatalog.Definition
public abstract class ClassCatalog extends Entity {

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Definition {
        String[] selectorProps() default {};
    }

    public ClassCatalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }
}
