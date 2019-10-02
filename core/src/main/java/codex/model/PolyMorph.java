package codex.model;

import codex.command.EntityCommand;
import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PolyMorph extends ClassCatalog implements IModelListener {

    private final static String PROP_IMPL_CLASS = "class";
    private final static String PROP_IMPL_VIEW  = "implementation";
    private final static String PROP_IMPL_PROPS = "properties";
    private final static List<String> PROP_FILTER = Stream.concat(
            EntityModel.SYSPROPS.stream(),
            Stream.of(
                    PROP_IMPL_CLASS,
                    PROP_IMPL_VIEW,
                    PROP_IMPL_PROPS
            )
    ).collect(Collectors.toList());

    @SuppressWarnings("unchecked")
    public static <E extends Entity> E newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        Class<? extends PolyMorph> polyMorphClass = getPolymorphClass(entityClass);

        PolyMorph newObject;
        if (polyMorphClass.equals(entityClass)) {
            // Clone (owner is a source object)
            newObject = Entity.newInstance(polyMorphClass, Entity.findOwner(owner.getValue().getParent()), PID);
            PolyMorph sourceObject = (PolyMorph) owner.getValue();
            newObject.setProperties(new LinkedHashMap<>(sourceObject.getProperties(true)));
            newObject.setImplementedClass(sourceObject.getImplementedClass());
        } else {
            // New Object
            newObject = Entity.newInstance(polyMorphClass, owner, PID);
            newObject.setImplementedClass(entityClass.asSubclass(PolyMorph.class));
        }
        return (E) newObject;
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

    private final IPropertyChangeListener labelUpdater = (propName, oldValue, newValue) -> {
        final PolyMorph implInstance = getImplementation(getImplementedClass());
        boolean paramChanged = !getProperties(true).get(propName).equals(getProperties(false).get(propName));
        implInstance.model.getEditor(propName).getLabel().setText(
                implInstance.model.getProperty(propName).getTitle() +
                (paramChanged ? " *" : "")
        );
    };

    private final IPropertyChangeListener parametersUpdater = (propName, oldValue, newValue) -> {
        Map<String, String> properties = getProperties(true);
        String paramVal = getImplementation(getImplementedClass()).model.getProperty(propName).getPropValue().toString();
        properties.put(propName, paramVal);
        setProperties(properties);

        labelUpdater.propertyChange(propName, null, null);
    };

    private Consumer<PolyMorph> initSEQ = polyMorph -> {
        polyMorph.model.getProperty(EntityModel.SEQ).removeChangeListener(polyMorph.model);
        polyMorph.model.setSEQ(0);
    };

    private Consumer<PolyMorph> initPID = polyMorph -> {
        polyMorph.model.getProperty(EntityModel.PID).removeChangeListener(polyMorph.model);
        polyMorph.model.setPID(getPID());
    };

    private INodeListener lockHandler = new INodeListener() {
        @Override
        public void childChanged(INode node) {
            if (islocked() ^ getImplementation().islocked()) {
                setLocked(getImplementation().islocked());
            }
        }
    };

    private PolyMorph implementation = null;

    public PolyMorph(EntityRef owner, String title) {
        super(
                owner,
                null,
                title,
                ""
        );

        //Properties
        model.addUserProp(PROP_IMPL_CLASS, new Str(null), false, Access.Select);
        model.addDynamicProp(PROP_IMPL_VIEW, new AnyType(), Access.Select, () -> getImplementedClass() == null ? null : new Iconified() {
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
        }, PROP_IMPL_CLASS);

        // Child object's properties map
        PropertyHolder<codex.type.Map<String, String>, Map<String, String>> propertiesHolder = new PropertyHolder<codex.type.Map<String, String>, Map<String, String>>(
                PROP_IMPL_PROPS,
                new codex.type.Map<>(Str.class, Str.class, new HashMap<>()),
                false
        ) {
            @Override
            public boolean isValid() {
                if (getImplementedClass() != null) {
                    PolyMorph implInstance = getImplementation();
                    return getParameters(implInstance).stream().allMatch(paramName -> implInstance.model.getProperty(paramName).isValid());
                }
                return true;
            }
        };
        model.addUserProp(propertiesHolder, Access.Select);

        // Property settings
        model.getEditor(PROP_IMPL_CLASS).setVisible(false);
        model.getEditor(PROP_IMPL_PROPS).setVisible(false);

        // Load implemented class
        setImplementedClass(getImplementedClass());
        model.addModelListener(this);
    }

    public PolyMorph getImplementation() {
        return getImplementation(getImplementedClass());
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

    private void activateCommands() {
        getCommands(getImplementation()).forEach(EntityCommand::activate);
    }

    private PolyMorph getImplementation(Class<? extends PolyMorph> implClass) {
        if (implClass != null && implementation == null) {
            implementation = Entity.newInstance(implClass, null, getPID());
            implementation.addNodeListener(lockHandler);
            initSEQ.accept(implementation);
        }
        return implementation;
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
        List<String> implParameters = getParameters(implementation);
        implParameters.stream()
                .filter((propName) -> !implementation.model.isPropertyDynamic(propName))
                .forEach((propName) -> ((AbstractEditor) implementation.model.getEditor(propName)).setLocked(locked));
    }

    private List<String> getParameters(PolyMorph implInstance) {
        return implInstance.model.getProperties(Access.Edit).stream()
                .filter(propName -> !PROP_FILTER.contains(propName))
                .collect(Collectors.toList());
    }

    private Class<? extends PolyMorph> getImplementedClass() {
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
            linkImplementedParameters(getImplementation(getImplementedClass()));
        }
    }

    @SuppressWarnings("unchecked")
    private void linkImplementedParameters(PolyMorph implInstance) {
        Map<String, String> properties = getProperties(true);
        List<String> implParameters = getParameters(implInstance);

        implInstance.model.getProperties(Access.Any).forEach(paramName -> {
            if (implParameters.contains(paramName)) {
                if (properties.containsKey(paramName)) {
                    updateParameter(implInstance, paramName, properties.get(paramName));
                } else {
                    properties.put(paramName, implInstance.model.getProperty(paramName).getPropValue().toString());
                }
                // Disable child model default handlers
                implInstance.model.getProperty(paramName).removeChangeListener(implInstance.model);

                // Update parameters map and labels
                implInstance.model.getProperty(paramName).addChangeListener(parametersUpdater);
            } else {
                implInstance.model.getEditor(paramName).setVisible(false);
            }
        });

        if (getID() == null) {
            setProperties(properties);
        } else {
            // Remove obsolete properties
            properties.entrySet().removeIf(entry -> !implParameters.contains(entry.getKey()));

            // Invisible update parameters
            model.getProperty(PROP_IMPL_PROPS).getPropValue().setValue(properties);
        }

        getEditorPage().add(
                new JPanel() {{
                    setLayout(new BorderLayout());
                    add(implInstance.getEditorPage(), BorderLayout.CENTER);
                }},
                new GridBagConstraints() {{
                    insets = new Insets(0, 0, 0, 0);
                    fill = GridBagConstraints.HORIZONTAL;
                    gridwidth = 2;
                    gridx = 0;
                    gridy = (getEditorPage().getComponentCount() - 2) / 2 + 1;
                }}
        );
    }

    private void updateParameter(PolyMorph implInstance, String propName, String propVal) {
        implInstance.model.getProperty(propName).getPropValue().valueOf(propVal);
        ((AbstractEditor) implInstance.model.getEditor(propName)).updateUI();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getProperties(boolean unsaved) {
        return (Map<String, String>) (unsaved ?
                model.getUnsavedValue(PROP_IMPL_PROPS) :
                model.getValue(PROP_IMPL_PROPS)
        );
    }

    private void setProperties(Map<String, String> params) {
        model.setValue(PROP_IMPL_PROPS, params);
    }

    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        if (changes.contains(EntityModel.PID)) {
            initPID.accept(getImplementation());
        }
        if (changes.contains(PROP_IMPL_PROPS)) {
            getProperties(true).forEach((propName, propVal) -> labelUpdater.propertyChange(propName, null, null));
            activateCommands();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void modelRestored(EntityModel model, List<String> changes) {
        if (changes.contains(PROP_IMPL_PROPS)) {
            PolyMorph implInstance = getImplementation(getImplementedClass());
            Map<String, String> properties = getProperties(true);
            List<String> implParameters = getParameters(implInstance);

            implParameters.forEach(paramName -> {
                Object currentVal = implInstance.model.getValue(paramName);
                implInstance.model.getProperty(paramName).getPropValue().valueOf(properties.get(paramName));
                Object restoredVal = implInstance.model.getValue(paramName);
                implInstance.model.getProperty(paramName).getPropValue().setValue(currentVal);

                implInstance.model.getProperty(paramName).removeChangeListener(parametersUpdater);
                implInstance.model.setValue(paramName, restoredVal);
                implInstance.model.getProperty(paramName).addChangeListener(parametersUpdater);

                labelUpdater.propertyChange(paramName, null, null);
            });
        }
    }
}
