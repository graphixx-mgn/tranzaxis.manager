package codex.model;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PolyMorph extends ClassCatalog implements IModelListener {

    private final static String PROP_IMPL_CLASS = "class";
    private final static String PROP_IMPL_VIEW  = "implementation";
    @PropertyDefinition(condition = true)
    public  final static String PROP_IMPL_PARAM = "parameters";
    private final static List<String> SYSPROPS  = Arrays.asList(
        PROP_IMPL_CLASS,
        PROP_IMPL_VIEW,
        PROP_IMPL_PARAM
    );

    @SuppressWarnings("unchecked")
    public static <E extends Entity> E newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        Class<? extends PolyMorph> polyMorphClass = getPolymorphClass(entityClass);

        PolyMorph newObject;
        if (polyMorphClass.equals(entityClass)) {
            // Clone (owner is a source object)
            newObject = Entity.newInstance(polyMorphClass, Entity.findOwner(owner.getValue().getParent()), PID);
            PolyMorph sourceObject = (PolyMorph) owner.getValue();
            newObject.setImplementedClass(sourceObject.getImplementedClass());
        } else {
            // New Object
            newObject = Entity.newInstance(polyMorphClass, owner, PID);
            newObject.setImplementedClass(entityClass.asSubclass(PolyMorph.class));
        }
        return (E) newObject;
    }

    public static <E extends Entity> boolean isInstance(Class<E> entityClass) {
        return !entityClass.equals(getPolymorphClass(entityClass));
    }

    private static <E extends Entity> Class<? extends PolyMorph> getPolymorphClass(Class<E> entityClass) {
        Class<? super E> nextClass = entityClass;
        while (!nextClass.getSuperclass().equals(PolyMorph.class)) {
            nextClass = nextClass.getSuperclass();
        }
        return nextClass.asSubclass(PolyMorph.class);
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

    private final IPropertyChangeListener parametersUpdater = (propName, oldValue, newValue) -> {
        Map<String, String> parameters = getParameters(true);
        parameters.replace(propName, getImplementation().model.getProperty(propName).getPropValue().toString());
        setParameters(parameters);
    };

    private INodeListener lockHandler = new INodeListener() {
        @Override
        public void childChanged(INode node) {
            if (islocked() ^ getImplementation().islocked()) {
                setLocked(getImplementation().islocked());
            }
        }
    };

    private final boolean isInstance = !PolyMorph.this.getClass().equals(getPolymorphClass(PolyMorph.this.getClass()));
    private PolyMorph implementation = null;

    public PolyMorph(EntityRef owner, String title) {
        super(owner, null, title, "");

        try {
            Field model = Entity.class.getDeclaredField("model");
            model.setAccessible(true);
            model.set(this, new PolymorphModel(owner, getClass().asSubclass(Entity.class), getPID()));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        //Properties
        model.addDynamicProp(EntityModel.THIS, new AnyType(),
                Access.Edit,
                () -> new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return PolyMorph.this.getIcon();
                    }

                    @Override
                    public String toString() {
                        return PolyMorph.this.toString();
                    }
                },
                EntityModel.PID
        );
        model.addUserProp(PROP_IMPL_CLASS, new Str(null), false, Access.Any);
        model.addDynamicProp(
                PROP_IMPL_VIEW,
                new AnyType(),
                !isInstance && ICatalog.class.isAssignableFrom(getClass()) ? Access.Any : Access.Select,
                () -> getImplementedClass() == null ?
                        null :
                        new Iconified() {
                            private final Class<? extends PolyMorph> implClass = getImplementedClass();
                            private final EntityDefinition entityDef = Entity.getDefinition(implClass);

                            @Override
                            public ImageIcon getIcon() {
                                return ImageUtils.getByPath(entityDef.icon());
                            }

                            @Override
                            public String toString() {
                                String className = Language.get(implClass, entityDef.title());
                                return className.equals(Language.NOT_FOUND) ? implClass.getTypeName() : className;
                            }
                        },
                PROP_IMPL_CLASS
        );

        // Child object's properties map
        PropertyHolder<codex.type.Map<String, String>, Map<String, String>> propertiesHolder = new PropertyHolder<codex.type.Map<String, String>, Map<String, String>>(
                PROP_IMPL_PARAM,
                new codex.type.Map<>(Str.class, Str.class, new HashMap<>()),
                false
        );
        model.addUserProp(propertiesHolder, Access.Any);

        // Load implemented class
        setImplementedClass(getImplementedClass());
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        if (!isInstance && getImplementation() != null) {
            return getImplementation().getChildClass();
        }
        return super.getChildClass();
    }

    @Override
    public boolean allowModifyChild() {
        if (!isInstance && getImplementation() != null) {
            return getImplementation().allowModifyChild();
        }
        return super.allowModifyChild();
    }

    public PolyMorph getImplementation() {
        return getImplementation(getImplementedClass());
    }

    private PolyMorph getImplementation(Class<? extends PolyMorph> implClass) {
        if (implClass != null && implementation == null) {
            implementation = Entity.newInstance(implClass, null, getPID());
            implementation.addNodeListener(lockHandler);
        }
        return implementation;
    }

    public PolyMorph getBaseObject() {
        Entity owner = getOwner();
        return Entity.newInstance(
                getPolymorphClass(getClass()),
                owner == null ? null : owner.toRef(),
                getPID()
        );
    }

    private void setLocked(boolean locked) {
        if (locked) {
            try {
                getLock().acquire();
            } catch (InterruptedException e) {
                getLock().release();
            }
        } else {
            getLock().release();
        }
        getForeignProperties().stream()
                .filter(propName -> !implementation.model.isPropertyDynamic(propName))
                .forEach(propName -> ((AbstractEditor) implementation.model.getEditor(propName)).setLocked(locked));
    }

    private List<String> getForeignProperties() {
        return getImplementation().model.getProperties(Access.Any).stream()
                .filter(propName -> !PolyMorph.SYSPROPS.contains(propName))
                .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                .filter(propName -> !propName.equals(EntityModel.THIS))
                .collect(Collectors.toList());
    }

    private List<String> getOwnProperties() {
        return new LinkedList<String>() {{
            addAll(EntityModel.SYSPROPS);
            addAll(PolyMorph.SYSPROPS);
        }};
    }

    private Class<? extends PolyMorph> getImplementedClass() {
        if (!model.hasProperty(PROP_IMPL_CLASS)) {
            return null;
        }
        String implClassName = (String) model.getUnsavedValue(PROP_IMPL_CLASS);
        if (implClassName != null && !implClassName.isEmpty()) {
            try {
                return Class.forName(implClassName).asSubclass(PolyMorph.class);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void setImplementedClass(Class<? extends PolyMorph> implClass) {
        if (implClass != null) {
            EntityDefinition entityDef = Entity.getDefinition(implClass);
            if (entityDef != null && !entityDef.icon().isEmpty()) {
                setIcon(ImageUtils.getByPath(entityDef.icon()));
            }
            model.setValue(PROP_IMPL_CLASS, implClass.getTypeName());
            implementation = getImplementation(implClass);

            linkBaseParameters();
            linkChildParameters();
        }
    }

    private void linkBaseParameters() {
        Stream.of(
                EntityModel.ID,
                EntityModel.SEQ,
                EntityModel.PID
        ).forEach(propName -> {
            getImplementation().model.properties.replace(propName, model.getProperty(propName));
            model.getProperty(propName).addChangeListener(this);
        });
    }

    @SuppressWarnings("unchecked")
    private void linkChildParameters() {
        Map<String, String> parameters = getParameters(true);
        List<String> foreignProps = getForeignProperties();
        foreignProps.forEach(foreignPropName -> {
            PropertyHolder propHolder = getImplementation().model.properties.get(foreignPropName);

            // Inject foreign property
            model.addProperty(propHolder, getImplementation().model.restrictions.get(foreignPropName));
            // Show foreign properties changes
            propHolder.addChangeListener(this);

            if (getImplementation().model.isPropertyDynamic(propHolder.getName())) {
                model.dynamicProps.add(propHolder.getName());
            } else {
                if (parameters.containsKey(foreignPropName)) {
                    updateParameter(foreignPropName, parameters.get(foreignPropName));
                } else {
                    parameters.put(foreignPropName, model.getProperty(foreignPropName).getPropValue().toString());
                }
                // Parameters map updater
                model.getProperty(foreignPropName).addChangeListener(parametersUpdater);
            }
        });

        // Inject property groups
        foreignProps.stream()
                .filter(propName -> getImplementation().model.getPropertyGroup(propName) != null)
                .collect(Collectors.groupingBy(propName -> getImplementation().model.getPropertyGroup(propName)))
                .forEach((groupName, propNames) -> {
                    model.addPropertyGroup(groupName, propNames.toArray(new String[] {}));
                });

        if (getID() == null) {
            setParameters(parameters);
        } else {
            if (parameters.entrySet().stream().anyMatch(entry -> !foreignProps.contains(entry.getKey()))) {
                //TODO: Возожно очистку ненужных свойств лучше сделать при сохранении
                parameters.entrySet().removeIf(entry -> !foreignProps.contains(entry.getKey()));
                try {
                    model.commit(false, PROP_IMPL_PARAM);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Invisible update parameters
            model.getProperty(PROP_IMPL_PARAM).getPropValue().setValue(parameters);
        }
    }

    private void updateParameter(String propName, String propVal) {
        model.getProperty(propName).getPropValue().valueOf(propVal);
        ((AbstractEditor) model.getEditor(propName)).updateUI();
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

    @Override
    @SuppressWarnings("unchecked")
    protected final List<EntityCommand<Entity>> getCommands(Entity entity) {
        PolyMorph polyMorph = (PolyMorph) entity;
        Class<? extends Entity>    thisClass = this.getClass();
        Class<? extends PolyMorph> implClass = IComplexType.coalesce(polyMorph.getImplementedClass(), entity.getClass().asSubclass(PolyMorph.class));

        return Stream.concat(
                    getClassHierarchy(implClass).stream().filter(thisClass::isAssignableFrom),
                    Stream.of(implClass)
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


    private class PolymorphModel extends EntityModel {

        PolymorphModel(EntityRef owner, Class<? extends Entity> entityClass, String PID) {
            super(owner, entityClass, PID);
        }

        @Override
        public String getQualifiedName() {
            return MessageFormat.format(
                    "[{0}/#{1}-''{2}'']",
                    getPolymorphClass(entityClass).getSimpleName(),
                    getID() == null ? "?" : getID(),
                    IComplexType.coalesce(getPID(getID() != null), "<new>")
            );
        }

        @Override
        protected void addProperty(PropertyHolder propHolder, Access restriction) {
            if (isInstance) {
                if (!PolyMorph.SYSPROPS.contains(propHolder.getName())) {
                    super.addProperty(propHolder, restriction);
                }
            } else {
                if (hasProperty(propHolder.getName())) {
                    if (getID() == null) {
                        properties.replace(propHolder.getName(), propHolder);
                        // If editor has been cached it is necessary to change it as well
                        if (editors.containsKey(propHolder.getName())) {
                            IEditor oldEditor = editors.get(propHolder.getName());
                            @SuppressWarnings("unchecked")
                            IEditor newEditor = propHolder.getPropValue().editorFactory().newInstance(propHolder);
                            newEditor.setEditable(oldEditor.isEditable());
                            newEditor.setVisible(oldEditor.isVisible());
                            editors.replace(propHolder.getName(), newEditor);
                        }
                    }
                    propHolder.addChangeListener(this);
                } else {
                    super.addProperty(propHolder, restriction);
                }
            }
        }

        @Override
        void commit(boolean showError, List<String> propNames) throws Exception {
            if (isInstance) {
                PolyMorph baseObject = getBaseObject();
                baseObject.model.commit(showError, new LinkedList<String>() {{
                    addAll(propNames);
                    add(PolyMorph.PROP_IMPL_PARAM);
                }});
            } else {
                final List<String> foreignProps = getForeignProperties();
                if (getID() == null) {
                    OrmContext.debug("Insert model to database {0}", getQualifiedName());
                    if (create(showError)) {
                        update(showError, getChanges().stream()
                                .filter(propName -> !foreignProps.contains(propName))
                                .collect(Collectors.toList())
                        );
                    }
                } else {
                    OrmContext.debug("Update model in database {0}", getQualifiedName());
                    update(showError, propNames.stream()
                            .filter(propName -> !foreignProps.contains(propName))
                            .collect(Collectors.toList())
                    );
                }
                List<String> notCommitted = getChanges();
                notCommitted.forEach(propName -> {
                    undoRegistry.delete(propName);
                    getImplementation().model.undoRegistry.delete(propName);
                });
                // Fire event to activate commands
                new LinkedList<>(modelListeners).forEach((listener) -> listener.modelSaved(this, notCommitted));
                // Fire event to update linked dynamic properties
                new LinkedList<>(getImplementation().model.modelListeners).forEach((listener) -> listener.modelSaved(this, notCommitted));
            }
        }

        @Override
        protected boolean create(boolean showError) throws Exception {
            Integer ownerId = getOwner() == null ? null : getOwner().getID();
            try {
                synchronized (this) {
                    getConfigService().initClassInstance(
                            entityClass,
                            (String) getProperty(PID).getPropValue().getValue(),
                            getOwnProperties().stream()
                                    .filter(propName -> !isPropertyDynamic(propName) || EntityModel.ID.equals(propName))
                                    .collect(Collectors.toMap(
                                            propName -> propName,
                                            propName -> getProperty(propName).getPropValue(),
                                            (u, v) -> {
                                                throw new IllegalStateException(String.format("Duplicate key %s", u));
                                            },
                                            LinkedHashMap::new
                                    )),
                            ownerId
                    ).forEach(this::setValue);
                    return true;
                }
            } catch (Exception e) {
                OrmContext.error("Unable initialize model in database", e);
                if (showError) {
                    MessageBox.show(
                            MessageType.ERROR,
                            MessageFormat.format(
                                    Language.get(EntityModel.class, "error@notsaved"),
                                    e.getMessage()
                            )
                    );
                }
                throw e;
            }
        }
    }
}
