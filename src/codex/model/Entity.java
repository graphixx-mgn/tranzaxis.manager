package codex.model;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.editor.AbstractEditor;
import codex.editor.IEditor;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.Language;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements IPropertyChangeListener, Iconified {
   
    private final static IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
    
    private       String        title;
    private final ImageIcon     icon;
    private final String        hint;
    
    private SelectorPresentation selectorPresentation;
    private final Map<String, EntityCommand> commands = new LinkedHashMap<>();
     
    /**
     * Модель сущности, контейнер всех её свойств.
     */
    public final EntityModel model;
    
    /**
     * Конструктор сущности.
     * @param icon Иконка для отображения в дереве проводника.
     * @param title Название сущности, уникальный ключ.
     * @param hint Описание сущности.
     */
    public Entity(EntityRef parent, ImageIcon icon, String title, String hint) {
        String PID = null;
        if (title != null) {
            String name = Language.get(this.getClass().getSimpleName(), title, new java.util.Locale("en", "US"));
            PID  = name.equals(Language.NOT_FOUND) ? title : name;
        
            String localTitle = Language.get(this.getClass().getSimpleName(), title);
            this.title = localTitle.equals(Language.NOT_FOUND) ? title : localTitle;
        }
        this.icon  = icon;
        this.hint  = hint;
        
        EntityRef ownerRef;
        if (parent != null) {
            if (parent.isLoaded()) {
                Entity owner = getOwner(parent.getValue());
                ownerRef = owner != null ? owner.toRef() : new EntityRef(null);
            } else {
                ownerRef = parent;
            }
        } else {
            ownerRef = new EntityRef(null);
        }
        
        this.model = new EntityModel(
                ownerRef,
                this.getClass(), 
                PID
        ) {
            @Override
            public IEditor getEditor(String name) {
                AbstractEditor editor = (AbstractEditor) super.getEditor(name);
                editor.setLocked(islocked());
                return editor;
            }

            @Override
            public boolean remove() {
                boolean result = super.remove();
                if (result) {
                    CACHE.removeEntity(Entity.this);
                }
                return result;
            }

        };
        this.model.addChangeListener(this);
        this.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(EntityModel.PID)) {
                    setTitle(model.getPID());
                }
            }
        });
    }

    @Override
    public void insert(INode child) {
        super.insert(child);
        
        Entity      childEntity = (Entity) child;
        EntityModel childModel  = childEntity.model;
        EntityModel parentModel = this.model;

        List<String> overrideProps = parentModel.getProperties(Access.Edit)
                .stream()
                .filter(propName -> childModel.hasProperty(propName) && !EntityModel.SYSPROPS.contains(propName))
                .collect(Collectors.toList());
        if (!overrideProps.isEmpty()) {
            overrideProps.forEach((propName) -> {
                if (!childModel.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                    return command instanceof OverrideProperty;
                })) {
                    childModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName));
                }
            });
        }

        childEntity.maintenanceModel();
        
        if (!((Entity) child).model.getProperty(EntityModel.OWN).isEmpty()) {
            return;
        }
        Entity owner = getOwner(((Entity) child).getParent());
        if (owner != null) {
            ((Entity) child).model.setOwner(owner);
        }
    }
    
    /**
     * Установить наименование сущности.
     */
    public final void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * Добавление новой команды сущности.
     */
    public final void addCommand(EntityCommand command) {
        commands.put(command.getName(), command);
    }
    
    /**
     * Получение команды по имени.
     */
    public final EntityCommand getCommand(String name) {
        if (!commands.containsKey(name)) {
            throw new NoSuchFieldError(
                    MessageFormat.format("Entity does not have command ''{0}''", name)
            );
        }
        return commands.get(name);
    }
    
    /**
     * Получение списка имеющихся команд сущности.
     */
    public final List<EntityCommand> getCommands() {
        return new LinkedList<>(commands.values());
    }
    
    /**
     * Возвращает иконку сущности.
     */
    @Override
    public final ImageIcon getIcon() {
        return icon;
    }
    
    /**
     * Возвращает описание сущности.
     */
    public final String getHint() {
        return hint;
    }
    
    void maintenanceModel() {
        if (model.getID() != null) {
            Map<String, String> databaseValues = CAS.readClassInstance(getClass(), model.getID());
            if (!databaseValues.isEmpty()) {
                List<String> propList = model.getProperties(Access.Any).stream().filter((propName) -> {
                    return !model.isPropertyDynamic(propName);
                }).collect(Collectors.toList());
                
                List<String> extraProps = databaseValues.keySet().stream().filter((propName) -> {
                    return !propList.contains(propName) && !EntityModel.SYSPROPS.contains(propName);
                }).collect(Collectors.toList());
                
                Map<String, IComplexType> absentProps = propList.stream()
                        .filter((propName) -> {
                            return !databaseValues.containsKey(propName);
                        })
                        .collect(Collectors.toMap(
                                propName -> propName, 
                                propName -> model.getProperty(propName).getPropValue()
                        ));
                
                if (!extraProps.isEmpty() || !absentProps.isEmpty()) {
                    CAS.maintainClassCatalog(getClass(), extraProps, absentProps);
                }
            }
        }
    }

    @Override
    public final SelectorPresentation getSelectorPresentation() {
        if (getChildClass() == null) return null;
        if (selectorPresentation == null) {
            selectorPresentation = new SelectorPresentation(this);
        }
        return selectorPresentation;
    };

    @Override
    public final EditorPresentation getEditorPresentation() {
        return new EditorPresentation(this);
    };
    
    public final List<String> getInvalidProperties() {
        return model.editors.entrySet().stream()
                .filter((entry) -> {
                    return !entry.getValue().stopEditing();
                }).map((entry) -> {
                    return entry.getKey();
                }).collect(Collectors.toList());
    }

    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        Logger.getLogger().debug(
                "Property ''{0}/{1}@{2}'' has been changed: ''{3}'' -> ''{4}''", 
                this.getClass().getSimpleName(), this, name, oldValue, (newValue instanceof IComplexType) ? 
                        ((IComplexType) newValue).getValue() : newValue
        );
    }
    
    /**
     * Проверка свойств на корректность значений.
     * При наличии некорректного свойсва вызывается диалог ошибки.
     */
    public final boolean validate() {
        List<String> invalidProps = getInvalidProperties().stream()
            .map((propName) -> {
                return model.getProperty(propName).getTitle();
            })
            .collect(Collectors.toList());
        if (!invalidProps.isEmpty()) {
            // Имеются ошибки в значениях
            MessageBox.show(
                    MessageType.ERROR, null, 
                    MessageFormat.format(Language.get("error@invalidprop"), String.join("\n", invalidProps)),
                    (event) -> {
                        model.editors.get(getInvalidProperties().get(0)).getFocusTarget().requestFocus();
                    }
            );
            return false;
        } else {
            return true;
        }
    }
    
     /**
     * Проверка допустимости закрытия модели при переходе на другую сущность в
     * дереве проводника.
     */
    public final boolean close() {
        if (validate() && model.hasChanges()) {
            // Предлагаем сохранить
            MessageBox.show(
                    MessageType.CONFIRMATION, null, 
                    Language.get("error@unsavedprop"), 
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            model.commit();
                        }
                    }
            );
        }
        return !model.hasChanges();
    };
    
    public final void stopEditing() {
        model.editors.values().stream().forEach((editor) -> {
            editor.stopEditing();
        });
    }
    
    @Override
    public final String toString() {
        return IComplexType.coalesce(title, "<new "+getClass().getSimpleName()+">");
    }
    
    public final EntityRef toRef() {
        EntityRef ref = new EntityRef(this.getClass());
        ref.setValue(this);
        return ref;
    }
    
    private static final EnityCache CACHE = EnityCache.getInstance();
    
    public static synchronized Entity newInstance(Class entityClass, EntityRef parent, String title) {
        try {
            Entity found = CACHE.findEntity(
                entityClass, 
                parent == null ? null : (
                    !parent.isLoaded() ? parent.getId() : (
                            getOwner(parent.getValue()) != null ? getOwner(parent.getValue()).model.getID() : null
                    )
                ),
                title
            );
            if (found != null) {
                if (parent != null) {
                    if (parent.isLoaded()) {
                        parent.getValue().insert(found);
                    }
                }
                return found;
            } else {
                Entity instance = (Entity) entityClass.getConstructor(EntityRef.class, String.class).newInstance(parent, (Object) title);
                instance.maintenanceModel();
                CACHE.addEntity(instance);
                return instance;
            }
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            Logger.getLogger().error(
                    MessageFormat.format("Unable instantiate entity ''{0}''", entityClass.getCanonicalName()), e
            );
        } catch (NoSuchMethodException e) {
            Logger.getLogger().error("Entity ''{0}'' does not have universal constructor (EntityRef, String)", entityClass.getCanonicalName());
        }
        return null;
    }
    
    static Entity getOwner(INode from) {
        Entity owner = (Entity) from;
        while (owner != null && Catalog.class.isAssignableFrom(owner.getClass())) {
            owner = (Entity) owner.getParent();
        }
        return owner;
    }
    
}
