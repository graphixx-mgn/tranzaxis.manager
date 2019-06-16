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
import codex.type.*;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements IPropertyChangeListener, Iconified {

    private static final ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    private static final ImageIcon ICON_LOCKED  = ImageUtils.getByPath("/images/lock.png");
   
    private static final Boolean DEV_MODE = "1".equals(java.lang.System.getProperty("showSysProps"));
    private static final EntityCache     CACHE = EntityCache.getInstance();
    private static final CommandRegistry COMMAND_REGISTRY = CommandRegistry.getInstance();

    private       String    title;
    private final ImageIcon icon;
    private final String    hint;

    Boolean isPrototype = false;

    private EditorPage           editorPage;
    private EditorPresentation   editorPresentation;
    private SelectorPresentation selectorPresentation;
     
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
        String PID;
        if (title == null) {
            if (!Catalog.class.isAssignableFrom(getClass())) {
                PID = null;
            } else {
                PID = Language.get(this.getClass(), "title", new java.util.Locale("en", "US"));
                this.title = Language.get(this.getClass(), "title");
            }
        } else {
            PID = title;
            this.title = title;
        }
        this.icon  = icon != null ? icon : new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
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
                            EntityRef ref = EntityRef.build(link.entryClass, link.entryID);
                            ref.getValue().fireChangeEvent();
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
                    CACHE.cache(this, PID, owner == null ? null : owner.getId());
                }
            }
        }

        //Properties
        model.addDynamicProp(EntityModel.THIS, new AnyType(),
                Access.Edit,
                () -> new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return Entity.this.getIcon();
                    }

                    @Override
                    public String toString() {
                        return Entity.this.toString();
                    }
                }
        );
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
        if (isPrototype) {
            return icon;
        } else if (islocked()) {
            return ImageUtils.combine(ImageUtils.grayscale(icon), ICON_LOCKED);
        } else if (!model.isValid()) {
            return ImageUtils.combine(icon, ICON_INVALID);
        } else {
            return (getMode() & INode.MODE_ENABLED) == INode.MODE_ENABLED ? icon : ImageUtils.grayscale(icon);
        }
    }

    protected final void setIcon(ImageIcon icon) {
        if (icon != null) {
            this.icon.setImage(icon.getImage());
            fireChangeEvent();
            model.updateDynamicProps(EntityModel.THIS);
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

    public boolean isOverridable() {
        return true;
    }

    @Override
    public void insert(INode child) {
        if (child.getParent() != this) {
            super.insert(child);
        }

        if (isOverridable()) {
            Entity childEntity = (Entity) child;
            EntityModel childModel = childEntity.model;
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
     * Получение списка имеющихся команд сущности.
     */
    public final List<EntityCommand> getCommands() {
        return new LinkedList<>(COMMAND_REGISTRY.getRegisteredCommands(getClass()));
    }

    /**
     * Получение команды по имени.
     * Устаревший метод. Следует использовать метод {@link Entity#getCommand(Class)}.
     */
    @Deprecated
    public final EntityCommand getCommand(String name) {
        return getCommands().stream()
                .filter(command -> command.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodError(MessageFormat.format("Entity does not have command ''{0}''", name)));
    }

    /**
     * Получение команды по классу.
     */
    @SuppressWarnings("unchecked")
    public final <E extends EntityCommand<? extends Entity>> E getCommand(Class<E> commandClass) {
        return (E) getCommands().stream()
                .filter(command -> command.getClass().equals(commandClass))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodError(MessageFormat.format("Entity does not have command of class ''{0}''", commandClass)));
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

    protected void setPropertyRestriction(String propName, Access access) {
        if (model.hasProperty(propName)) {
            model.restrictions.put(propName, access);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (!ISerializableType.class.isAssignableFrom(model.getPropertyType(name))) return;

        if (
                (oldValue == null && newValue != null) ||
                (oldValue != null && newValue == null) ||
                (oldValue != null && !oldValue.equals(newValue))
        ) {
            Logger.getLogger().debug(
                    "Property ''{0}@{1}'' has been changed: {2} -> {3}", 
                    model.getQualifiedName(), name, 
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            oldValue instanceof IComplexType ? ((IComplexType) oldValue).getValue() : oldValue
                    ), 
                    model.getProperty(name).getPropValue().getQualifiedValue(
                            newValue instanceof IComplexType ? ((IComplexType) newValue).getValue() : newValue
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
                    (event) -> model.editors.get(getInvalidProperties().get(0)).getFocusTarget().requestFocus()
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
    
    public static <E extends Entity> E newPrototype(Class<E> entityClass) {
        if (entityClass.isMemberClass()) {
            throw new IllegalStateException(MessageFormat.format(
                    "Entity class ''{0}'' is inner class of ''{1}''", 
                    entityClass.getSimpleName(),
                    entityClass.getEnclosingClass().getCanonicalName()
            ));
        }
        try {
            Constructor<E> ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
            ctor.setAccessible(true);
            E prototype = ctor.newInstance(null, null);
            prototype.isPrototype = true;
            return prototype;
        } catch (InvocationTargetException | ExceptionInInitializerError | InstantiationException | IllegalAccessException e) {
            Throwable exception = e;
            do {
                Logger.getLogger().error(
                        MessageFormat.format("Unable instantiate entity prototype [Class: {0}]", entityClass.getCanonicalName()), exception
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
    
    public static <E extends Entity> E  newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        synchronized (entityClass) {
            final Entity found = CACHE.find(
                    entityClass,
                    owner == null ? null : owner.getId(),
                    PID != null ? PID : Language.get(entityClass, "title", new java.util.Locale("en", "US"))
            );
            if (found == null) {
                try {
                    Constructor<E> ctor = entityClass.getDeclaredConstructor(EntityRef.class, String.class);
                    ctor.setAccessible(true);
                    final E created = ctor.newInstance(owner, PID);
                    if (created.getPID() == null) {
                        created.model.addModelListener(new IModelListener() {
                            @Override
                            public void modelSaved(EntityModel model, List<String> changes) {
                                if (changes.contains(EntityModel.PID)) {
                                    CACHE.cache(created, created.getPID(), owner == null ? null : owner.getId());
                                }
                            }
                        });
                    } else if (Language.NOT_FOUND.equals(created.getPID())) {
                        throw new IllegalStateException(MessageFormat.format(
                                "Localization string 'title' not defined for class ''{0}''", entityClass
                        ));
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
            //noinspection unchecked
            return (E) found;
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
