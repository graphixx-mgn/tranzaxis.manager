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
import codex.presentation.EditorPage;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.Language;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements IPropertyChangeListener, Iconified {
   
    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showSysProps"));
    private static final EntityCache CACHE = EntityCache.getInstance();

    private       String    title;
    private final ImageIcon icon;
    private final String    hint;

    final String origTitle;

    private EditorPage           editorPage;
    private EditorPresentation   editorPresentation;
    private SelectorPresentation selectorPresentation;
    private final Map<String, EntityCommand<Entity>> commands = new LinkedHashMap<>();
     
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
    public Entity(EntityRef owner, ImageIcon icon, String title, String hint) {
        String PID = null;
        if (title != null) {
            String name = Language.get(this.getClass(), title, new java.util.Locale("en", "US"));
            PID  = name.equals(Language.NOT_FOUND) ? title : name;
            String localTitle = Language.get(this.getClass(), title);
            this.title = localTitle.equals(Language.NOT_FOUND) ? PID : localTitle;
        }
        origTitle  = title;
        this.icon  = icon;
        this.hint  = hint;
        this.model = new EntityModel(
                owner,
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
            boolean remove(boolean readAfter) {
                if (getID() == null) {
                    CACHE.remove(Entity.this);
                    if (readAfter) read();
                    return true;
                } else {
                    boolean success = super.remove(readAfter);
                    if (success) CACHE.remove(Entity.this);
                    return success;
                }
            }
        };
        this.model.addChangeListener(this);
        this.model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(EntityModel.PID)) {
                    setTitle(model.getPID(false));
                }
                ((IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class)).findReferencedEntries(Entity.this.getClass(), getID()).stream()
                        .filter(link -> link.isIncoming)
                        .forEach(link -> {
                            try {
                                EntityRef ref = EntityRef.build(Class.forName(link.entryClass), link.entryID);
                                ref.getValue().fireChangeEvent();
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        });
            }
        });
        if (getPID() != null) {
            synchronized (this.getClass()) {
                final Entity found = CACHE.find(
                    this.getClass(),
                    owner == null ? null : owner.getId(),
                    PID
                );
                if (found == null) {
                    CACHE.cache(this, title, owner == null ? null : owner.getId());
                }
            }
        }
    }
    
    /**
     * Возвращает наименование сущности.
     */
    public final String getTitle() {
        return title;
    }
    
    /**
     * Установить наименование сущности.
     */
    public final void setTitle(String title) {
        this.title = title;
    }
    
    /**
     * Возвращает иконку сущности.
     */
    @Override
    public final ImageIcon getIcon() {
        return icon;
    }

    protected final void setIcon(ImageIcon icon) {
        if (icon != null) {
            this.icon.setImage(icon.getImage());
            fireChangeEvent();
        }
    }
    
    /**
     * Возвращает описание сущности.
     */
    public final String getHint() {
        return hint;
    }
    
    public final Integer getID() {
        return model.getID();
    }
    
    public final String getPID() {
        return model.getPID(false);
    }
    
    public final Integer getSEQ() {
        return model.getSEQ();
    }
    
    public final Entity getOwner() {
        return model.getOwner();
    }
    
    public final List<String> getOverride() {
        return model.getOverride();
    }
    
    public final Entity setID(Integer id) {
        model.setID(id);
        return this;
    }
    
    public final Entity setPID(String pid) {
        model.setPID(pid);
        return this;
    }
    
    public final Entity setSEQ(Integer seq) {
        model.setSEQ(seq);
        return this;
    }
    
    public final void setOverride(List<String> value) {
        model.setOverride(value);
    }

    @Override
    public void insert(INode child) {
        if (child.getParent() != this) {
            super.insert(child);
        }
        
        Entity      childEntity = (Entity) child;
        EntityModel childModel  = childEntity.model;
        EntityModel parentModel = this.model;

        List<String> overrideProps = parentModel.getProperties(Access.Edit)
                .stream()
                .filter(
                        propName -> 
                                childModel.hasProperty(propName) && 
                                !childModel.isPropertyDynamic(propName) &&
                                !EntityModel.SYSPROPS.contains(propName) &&
                                parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                ).collect(Collectors.toList());
        if (!overrideProps.isEmpty()) {
            overrideProps.forEach((propName) -> {
                if (!childModel.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                    return command instanceof OverrideProperty;
                })) {
                    childModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName));
                }
            });
        }
    }
    
    @Override
    public void delete(INode child) {
        Entity childEntity = (Entity) child;
        if (childEntity.isAutoGenerated()) {
            if (childEntity.getID() == null) {
                CACHE.remove(childEntity);
            }
            super.delete(child);
        } else if (childEntity.model.remove(false)) {
            super.delete(child);
        }
    }
    
    /**
     * Добавление новой команды сущности.
     */
    public final Entity addCommand(EntityCommand command) {
        commands.put(command.getName(), command);
        return this;
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
    public final List<EntityCommand<Entity>> getCommands() {
        return new LinkedList<>(commands.values());
    }

    protected boolean isAutoGenerated() {
        return false;
    }

    /**
     * Возвраящает страницу редактора модели.
     */
    @Override
    public final EditorPage getEditorPage() {
        if (editorPage == null) {
            editorPage = new EditorPage(model);
        }
        return editorPage;
    }
    
    @Override
    public final SelectorPresentation getSelectorPresentation() {
        if (getChildClass() == null) return null;
        if (selectorPresentation == null) {
            selectorPresentation = new SelectorPresentation(this);
        }
        return selectorPresentation;
    }

    @Override
    public final EditorPresentation getEditorPresentation() {
        if (editorPresentation == null && !model.getProperties(Access.Edit).isEmpty()) {
            editorPresentation = new EditorPresentation(this);
        }
        return editorPresentation;
    }
    
    public final List<String> getInvalidProperties() {
        return model.editors.entrySet().stream()
                .filter((entry) -> !entry.getValue().stopEditing())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (
                (oldValue == null && newValue != null) || 
                (oldValue != null && newValue == null) ||
                (oldValue != null && newValue != null && !oldValue.equals(newValue))
        ) {
            Logger.getLogger().debug(
                    "Property ''{0}@{1}'' has been changed: {2} -> {3}", 
                    model.getQualifiedName(), name, 
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            oldValue != null && oldValue instanceof IComplexType ? ((IComplexType) oldValue).getValue() : oldValue
                    ), 
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            newValue != null && newValue instanceof IComplexType ? ((IComplexType) newValue).getValue() : newValue
                    )
            );
        }
    }
    
    /**
     * Проверка свойств на корректность значений.
     * При наличии некорректного свойсва вызывается диалог ошибки.
     */
    public final boolean validate() {
        List<String> invalidProps = getInvalidProperties().stream()
            .map((propName) -> model.getProperty(propName).getTitle())
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
                            try {
                                model.commit(true);
                            } catch (Exception e) {
                                // Do nothing
                            }
                        }
                    }
            );
        }
        return !model.hasChanges();
    }
    
    public final void stopEditing() {
        model.editors.values().forEach(IEditor::stopEditing);
    }
    
    @Override
    public final String toString() {
        String title = getTitle();
        if (DEV_MODE && title != null && getID() != null) {
            title = title.concat(" (#").concat(Integer.toString(getID())).concat(")");
        }
        return IComplexType.coalesce(title, "<new "+getClass().getSimpleName()+">");
    }
    
    public final EntityRef toRef() {
        EntityRef ref = new EntityRef(this.getClass());
        ref.setValue(this);
        return ref;
    }
    
    public static Entity newPrototype(Class entityClass) {
        if (entityClass.isMemberClass()) {
            throw new IllegalStateException(MessageFormat.format(
                    "Entity class ''{0}'' is inner class of ''{1}''", 
                    entityClass.getSimpleName(),
                    entityClass.getEnclosingClass().getCanonicalName()
            ));
        }
        try {
            Constructor ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
            ctor.setAccessible(true);
            Entity prototype = (Entity) ctor.newInstance(null, null);
            //prototype.isPrototype = true;
            return prototype;
        } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
            Throwable exception = e;
            do {
                Logger.getLogger().error(
                        MessageFormat.format("Unable instantiate entity prototype [Class: {1}]", entityClass.getCanonicalName()), exception
                );
            } while ((exception = exception.getCause()) != null);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(MessageFormat.format(
                    "Entity ''{0}'' does not have universal constructor (EntityRef, String)", 
                    entityClass.getCanonicalName()
            ));
        }
        return null;
    }
    
    public static Entity newInstance(Class entityClass, EntityRef owner, String PID) {
        synchronized (entityClass) {
            final Entity found = CACHE.find(
                    entityClass,
                    owner == null ? null : owner.getId(),
                    PID
            );
            if (found == null) {
                try {
                    Constructor ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
                    ctor.setAccessible(true);
                    final Entity created = (Entity) ctor.newInstance(owner, PID);
                    if (created.getPID() == null) {
                        created.model.addModelListener(new IModelListener() {
                            @Override
                            public void modelSaved(EntityModel model, List<String> changes) {
                                if (changes.contains(EntityModel.PID)) {
                                    CACHE.cache(created, PID, owner == null ? null : owner.getId());
                                }
                            }
                        });
                    }
                    return created;
                } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
                    Throwable exception = e;
                    do {
                        Logger.getLogger().error(
                                MessageFormat.format("Unable instantiate entity ''{0}'' / [Class: {1}]", PID, entityClass.getCanonicalName()), exception
                        );
                    } while ((exception = exception.getCause()) != null);
                } catch (NoSuchMethodException e) {
                    Logger.getLogger().error(
                            "Entity ''{0}'' does not have universal constructor (EntityRef<owner>, String<PID>)", 
                            entityClass.getCanonicalName()
                    );
                }
            }
            return found;
        }
    }
    
    public static EntityRef findOwner(INode from) {
        INode next = from;
        while (next != null && Catalog.class.isAssignableFrom(next.getClass())) {
            next = (INode) next.getParent();
        }
        if (next == null) {
            return null;
        } else {
            Entity found = (Entity) next;
            if (found.getID() == null) {
                Logger.getLogger().warn("Found uninitialized owner entity: {0}", found.model.getQualifiedName());
                return null;
            } else {
                return found.toRef();
            }
        }
    }
    
}
