package codex.model;

import codex.log.Logger;
import codex.type.EntityRef;
import org.atteo.classindex.ClassIndex;
import org.atteo.classindex.IndexSubclasses;
import javax.swing.*;
import java.lang.annotation.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@IndexSubclasses
@ClassCatalog.Definition
public abstract class ClassCatalog extends Entity {

    private static final List<Class<? extends ClassCatalog>> catalogs = new LinkedList<>();

    static {
        synchronized (catalogs) {
            catalogs.addAll(StreamSupport.stream(ClassIndex.getSubclasses(codex.model.ClassCatalog.class).spliterator(), false)
                    .filter(aClass -> !aClass.equals(PolyMorph.class))
                    .collect(Collectors.toList())
            );
            SwingUtilities.invokeLater(() -> Logger.getLogger().debug(
                    "Registered class catalogs:\n{0}",
                    catalogs.stream()
                        .collect(Collectors.groupingBy(aClass -> {
                            if (PolyMorph.class.isAssignableFrom(aClass)) {
                                return PolyMorph.getPolymorphClass(aClass);
                            } else {
                                return Object.class;
                            }
                        }))
                        .entrySet().stream()
                        .sorted(Comparator.comparing((Map.Entry<Class<? extends Object>, List<Class<? extends ClassCatalog>>> o) ->
                                o.getKey().getPackage().getName())
                                .thenComparing(o -> o.getKey().getSimpleName())
                        )
                        .map(classListEntry -> MessageFormat.format(
                                "[{0}]\n{1}",
                                classListEntry.getKey().getTypeName(),
                                classListEntry.getValue().stream()
                                        .filter(aClass -> !aClass.equals(classListEntry.getKey()))
                                        .sorted(Comparator.comparing((Class<?> o) ->
                                                o.getPackage().getName())
                                                .thenComparing(Class::getSimpleName)
                                        )
                                        .map(aClass -> " * ".concat(aClass.getTypeName()))
                                        .collect(Collectors.joining("\n"))
                        ))
                        .collect(Collectors.joining("\n"))
            ));
        }
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Definition {
        String[] selectorProps() default {};
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Domains {
        Class<? extends IDomain>[] value();
    }

    public interface IDomain {
        /* The inherited classes or interfaces must have localization tag 'title' */
    }

    ClassCatalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    /**
     * Возвращает список классов, разрешенных для создания и загрузки в данном каталоге.
     * Каждый из этих классов наследуется от одного класса {@link ClassCatalog}.
     */
    public synchronized static List<Class<? extends Entity>> getClassCatalog(Entity entity) {
        Class<? extends Entity> childClass = entity.getChildClass();
        if (childClass.isAnnotationPresent(ClassCatalog.Definition.class)) {
            return catalogs.stream()
                    .filter(childClass::isAssignableFrom)
                    .sorted(Comparator.comparing(Class::getTypeName))
                    .collect(Collectors.toList());
        } else {
            return new LinkedList<Class<? extends Entity>>() {{
                add(childClass);
            }};
        }
    }

    public synchronized static void registerClassCatalog(Class<? extends ClassCatalog> classCatalog) {
        catalogs.add(classCatalog);
        Logger.getLogger().debug("Register class catalog: {0}", classCatalog.getTypeName());
    }

    static synchronized Class<? extends ClassCatalog> forName(String className) throws ClassNotFoundException {
        return catalogs.stream()
                .filter(aClass -> aClass.getTypeName().equals(className))
                .findFirst().orElseThrow(() -> new ClassNotFoundException(className));
    }
}
