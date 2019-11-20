package codex.model;

import codex.command.EntityCommand;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PolyMorph extends ClassCatalog implements IModelListener {

    final static String PROP_IMPL_CLASS = "class";
    final static String PROP_IMPL_PARAM = "parameters";
    private final static List<String> SYSPROPS  = Arrays.asList(
        PROP_IMPL_CLASS,
        PROP_IMPL_PARAM
    );

    static <E extends Entity> Class<? extends PolyMorph> getPolymorphClass(Class<E> entityClass) {
        Class<? super E> nextClass = entityClass;
        while (!nextClass.getSuperclass().equals(PolyMorph.class)) {
            nextClass = nextClass.getSuperclass();
        }
        return nextClass.asSubclass(PolyMorph.class);
    }

    static List<String> getExternalProperties(EntityModel model) {
        return model.getProperties(Access.Any).stream()
                .filter(propName -> !PolyMorph.SYSPROPS.contains(propName))
                .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                .filter(propName -> !propName.equals(EntityModel.THIS))
                .collect(Collectors.toList());
    }

    static Map<String, String> parseParameters(String serialized) {
        if (serialized != null && !serialized.isEmpty()) {
            List<String> list = ArrStr.parse(serialized);
            Map<String, String> values = new LinkedHashMap<>();
            for (int keyIdx = 0; keyIdx < list.size(); keyIdx = keyIdx+2) {
                values.put(list.get(keyIdx), list.get(keyIdx+1));
            }
            return values;
        } else {
            return Collections.emptyMap();
        }
    }

    private static List<Class<?>> getClassHierarchy(final Class<?> cls) {
        if (cls == null) {
            return null;
        }
        final List<Class<?>> classes = new ArrayList<>();
        Class<?> superclass = cls.getSuperclass();
        while (superclass != null && PolyMorph.class.isAssignableFrom(cls)) {
            classes.add(superclass);
            superclass = superclass.getSuperclass();
        }
        Collections.reverse(classes);
        return classes;
    }

    public PolyMorph(EntityRef owner, String title) {
        super(owner, null, title, "");
        final EntityDefinition entityDef = Entity.getDefinition(getClass());
        setIcon(ImageUtils.getByPath(entityDef.icon()));
        model.removeChangeListener(this);
        model.addChangeListener((name, oldValue, newValue) -> {
            if (!name.equals(PROP_IMPL_PARAM)) {
                this.propertyChange(name, oldValue, newValue);
            }
        });

        model.addUserProp(PROP_IMPL_CLASS, new Str(null), false, Access.Select);

        // Child object's properties map
        PropertyHolder<codex.type.Map<String, String>, Map<String, String>> propertiesHolder = new PropertyHolder<>(
                PROP_IMPL_PARAM,
                new codex.type.Map<>(Str.class, Str.class, new HashMap<>()),
                false
        );

        model.addUserProp(propertiesHolder, Access.Select);

        // Property settings
        model.setValue(PROP_IMPL_CLASS, getClass().getTypeName());
        model.addModelListener(this);

        model.getEditor(PROP_IMPL_CLASS).setVisible(false);
        model.getEditor(PROP_IMPL_PARAM).setVisible(false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getParameters(boolean unsaved) {
        return (Map<String, String>) (unsaved ?
                model.getUnsavedValue(PROP_IMPL_PARAM) :
                model.getValue(PROP_IMPL_PARAM)
        );
    }

    private void setParameters(Map<String, String> params) {
        model.setValue(PROP_IMPL_PARAM, params);
    }

//    //TODO: Удалить потом
//    public PolyMorph getImplementation() {
//        return this;
//    }
//
//    //TODO: Удалить потом
//    public PolyMorph getBaseObject() {
//        return this;
//    }

    @Override
    public void modelChanged(EntityModel model, List<String> changes) {
        changes.stream()
                .filter(propName -> !PolyMorph.SYSPROPS.contains(propName))
                .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                .filter(propName -> !propName.equals(EntityModel.THIS))
                .forEach(propName -> {
                    Map<String, String> parameters = getParameters(true);
                    parameters.put(propName, model.getProperty(propName).getPropValue().toString());
                    setParameters(parameters);
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final List<EntityCommand<Entity>> getCommands(Entity entity) {
        return Stream.concat(
                    getClassHierarchy(getClass()).stream().filter(PolyMorph.class::isAssignableFrom),
                    Stream.of(getClass())
               ).map(aClass -> {
                    LinkedList<EntityCommand<Entity>> commands = new LinkedList<>();
                    new LinkedList<>(CommandRegistry.getInstance().getRegisteredCommands(aClass.asSubclass(Entity.class))).forEach(entityCommand -> {
                        commands.add((EntityCommand<Entity>) entityCommand);
                    });
                    return commands;
                })
                .flatMap(Collection::stream)
               .collect(Collectors.toList());
    }
}
