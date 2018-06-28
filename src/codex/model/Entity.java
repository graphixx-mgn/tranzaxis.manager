package codex.model;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.IPropertyChangeListener;
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
   
    private       String        title;
    private final ImageIcon     icon;
    private final String        hint;
    
    private SelectorPresentation selectorPresentation;
    private final Map<String, EntityCommand> commands = new LinkedHashMap<>();
     
    /**
     * Модель сущности, контейнер всех её свойств.
     */
    public final EntityModel model;
    
    public Entity(INode parent, ImageIcon icon, String title, String hint) {
        this(icon, title, hint);
        if (parent != null) {
            parent.insert(this);
        }
    }
    
    /**
     * Конструктор сущности.
     * @param icon Иконка для отображения в дереве проводника.
     * @param title Название сущности, уникальный ключ.
     * @param hint Описание сущности.
     */
    public Entity(ImageIcon icon, String title, String hint) {
        String PID = null;
        if (title != null) {
            String name = Language.get(this.getClass().getSimpleName(), title, new java.util.Locale("en", "US"));
            PID  = name.equals(Language.NOT_FOUND) ? title : name;
        
            String localTitle = Language.get(this.getClass().getSimpleName(), title);
            this.title = localTitle.equals(Language.NOT_FOUND) ? title : localTitle;
        }
        this.icon  = icon;
        this.hint  = hint;
        this.model = new EntityModel(this.getClass(), PID);
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
        if (getParent() != null) {
            Entity parent = (Entity) getParent();
            List<String> overrideProps = parent.model.getProperties(Access.Edit)
                    .stream()
                    .filter(propName -> this.model.hasProperty(propName) && !EntityModel.SYSPROPS.contains(propName))
                    .collect(Collectors.toList());
            if (!overrideProps.isEmpty()) {
                overrideProps.forEach((propName) -> {
                    if (!this.model.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                        return command instanceof OverrideProperty;
                    })) {
                        this.model.getEditor(propName).addCommand(new OverrideProperty(
                                this,
                                this.model.getProperty(propName), 
                                parent.model.getProperty(propName)
                        ));
                    }
                });
            }
        }
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
                "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}''", 
                this, name, oldValue, (newValue instanceof IComplexType) ? 
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
    
    public static Entity newInstance(Class entityClass, INode parent, String title) {
        try {
            return (Entity) entityClass.getConstructor(INode.class, String.class).newInstance(parent, (Object) title);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            Logger.getLogger().error(
                    MessageFormat.format("Unable instantiate entity ''{0}''", entityClass.getCanonicalName()), e
            );
            return null;
        } catch (NoSuchMethodException e) {
            Logger.getLogger().error("Entity ''{0}'' does not have universal constructor (INode, String)", entityClass.getCanonicalName());
            return null;
        }
    }
    
}
