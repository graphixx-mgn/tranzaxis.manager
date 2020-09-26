package codex.model;

import codex.command.EntityCommand;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.type.EntityRef;
import codex.type.ISerializableType;
import codex.type.Str;
import codex.utils.Caller;
import codex.utils.ImageUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PolyMorph extends ClassCatalog implements IModelListener {

    @PropertyDefinition(state = true)
    final static String PROP_IMPL_CLASS = "class";

    @PropertyDefinition(state = true)
    final static String PROP_IMPL_PARAM = "parameters";
    final static String PROP_DB_COLUMNS = "columns";

    public static <E extends Entity> Class<? extends PolyMorph> getPolymorphClass(Class<E> entityClass) {
        Class<? super E> nextClass = entityClass;
        while (!nextClass.getSuperclass().equals(PolyMorph.class)) {
            nextClass = nextClass.getSuperclass();
        }
        return nextClass.asSubclass(PolyMorph.class);
    }

    static List<String> getExternalProperties(EntityModel model) {
        return model.getProperties(Access.Any).stream()
                .filter(propName -> !getDatabaseProps(model).contains(propName))
                .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                .filter(propName -> !propName.equals(EntityModel.THIS))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static List<String> getDatabaseProps(EntityModel model) {
        return model.hasProperty(PROP_DB_COLUMNS) ? (List<String>) model.getValue(PROP_DB_COLUMNS) : Collections.emptyList();
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

    @SuppressWarnings("unchecked")
    private static <E extends Entity> List<Class<E>> getClassHierarchy(final Class<E> cls) {
        if (cls == null) {
            return null;
        }
        final List<Class<E>> classes = new ArrayList<>();
        Class<E> superclass = cls;
        while (superclass != null && PolyMorph.class.isAssignableFrom(cls)) {
            classes.add(superclass);
            superclass = (Class<E>) superclass.getSuperclass();
        }
        Collections.reverse(classes);
        return classes;
    }

    private final List<String> databaseProps = new ArrayList<>();

    public PolyMorph(EntityRef owner, String title) {
        super(owner, null, title, "");

        model.addDynamicProp(PROP_DB_COLUMNS, new ArrStr(), Access.Any, () -> databaseProps);

        model.addUserProp(PROP_IMPL_CLASS, new Str(null), false, Access.Any);
        final Class<? extends Entity> implClass = getPolymorphClass(getClass()).equals(getClass()) ? getImplClass() : getClass();
        final EntityDefinition entityDef = Entity.getDefinition(implClass);
        setIcon(ImageUtils.getByPath(implClass, entityDef.icon()));

        // Child object's properties map
        PropertyHolder<codex.type.Map<String, String>, Map<String, String>> propertiesHolder = new PropertyHolder<>(
                PROP_IMPL_PARAM,
                new codex.type.Map<>(new Str(), new Str(), new HashMap<>()),
                false
        );
        model.addUserProp(propertiesHolder, Access.Any);

        databaseProps.add(PROP_IMPL_CLASS);
        databaseProps.add(PROP_IMPL_PARAM);

        // Property settings
        model.setValue(PROP_IMPL_CLASS, getClass().getTypeName());

        // Listeners
        model.removeChangeListener(this);
        model.addChangeListener((name, oldValue, newValue) -> {
            if (!getDatabaseProps(model).contains(name)) {
                this.propertyChange(name, oldValue, newValue);
            }
            if (
                !getDatabaseProps(model).contains(name) &&
                ISerializableType.class.isAssignableFrom(model.getPropertyType(name)) &&
                !EntityModel.SYSPROPS.contains(name) &&
                !model.isPropertyDynamic(name)
            ) {
                Map<String, String> parameters = getParameters();
                String serializedValue = model.getProperty(name).getPropValue().toString();
                if (!Objects.equals(serializedValue, parameters.get(name))) {
                    parameters.put(name, model.getProperty(name).getPropValue().toString());
                    setParameters(parameters);
                }
            }
        });
        model.addModelListener(this);
    }

    protected void registerColumnProperties(String... properties) {
        Class caller = Caller.getInstance().getClassStack().get(1);
        if (caller != PolyMorph.class && caller != getPolymorphClass(getClass())) {
            throw new IllegalStateException("Columns registration is not allowed in implementation class");
        }
        for (String propName : properties) {
            if (!model.hasProperty(propName)) {
                throw new IllegalStateException("Property '"+propName+"' does not exist in model");
            }
            if (databaseProps.contains(propName)) {
                throw new IllegalStateException("Property '"+propName+"' already registered");
            }
            databaseProps.add(propName);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getParameters() {
        return (Map<String, String>) (model.getUnsavedValue(PROP_IMPL_PARAM));
    }

    private void setParameters(Map<String, String> params) {
        params.keySet().removeIf(propName -> !model.hasProperty(propName));
        model.setValue(PROP_IMPL_PARAM, params);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final <E extends Entity> List<EntityCommand<E>> getCommands(E entity) {
        return Stream
                .concat(
                        getClassHierarchy((Class<E>) getClass()).stream().filter(PolyMorph.class::isAssignableFrom),
                        Stream.empty()
                )
                .map(aClass -> CommandRegistry.getInstance().getRegisteredCommands(entity, aClass))
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
        ;
    }

    private Class<? extends PolyMorph> getImplClass() {
        try {
            return Class.forName((String) model.getValue(PROP_IMPL_CLASS)).asSubclass(PolyMorph.class);
        } catch (ClassNotFoundException e) {
            return getClass().asSubclass(PolyMorph.class);
        }
    }
}
