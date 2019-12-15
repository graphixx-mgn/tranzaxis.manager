package codex.model;

import codex.type.EntityRef;
import org.atteo.classindex.ClassIndex;
import org.atteo.classindex.IndexSubclasses;
import javax.swing.*;
import java.lang.annotation.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@IndexSubclasses
@ClassCatalog.Definition
public abstract class ClassCatalog extends Entity {

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Definition {
        String[] selectorProps() default {};
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Domain {}

    ClassCatalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    /**
     * Возвращает список классов, разрешенных для создания и загрузки в данном каталоге.
     * Каждый из этих классов наследуется от одного класса {@link ClassCatalog}.
     */
    public static List<Class<? extends Entity>> getClassCatalog(Entity entity) {
        Class<? extends Entity> childClass = entity.getChildClass();
        if (childClass.isAnnotationPresent(ClassCatalog.Definition.class)) {
            return StreamSupport.stream(ClassIndex.getSubclasses(codex.model.ClassCatalog.class).spliterator(), false)
                    .filter(childClass::isAssignableFrom)
                    .sorted(Comparator.comparing(Class::getTypeName))
                    .collect(Collectors.toList());
        } else {
            return Collections.nCopies(1, childClass);
        }
    }
}
