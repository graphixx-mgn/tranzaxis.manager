package codex.model;

import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.IEditor;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.presentation.SwitchInheritance;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;
import codex.property.IPropertyChangeListener;
import codex.utils.Language;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements IPropertyChangeListener {
   
    private final ImageIcon icon;
    private final String    hint;
    private final String    title;
    
    private EditorPresentation   editorPresentation;
    private SelectorPresentation selectorPresentation;
    
    private final List<EntityCommand>  commands = new LinkedList<>();
    private final Map<String, IEditor> editors  = new LinkedHashMap<>();
     
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
    public Entity(ImageIcon icon, String title, String hint) {
        String PID;
        String localTitle = Language.lookup(Arrays.asList(new String[]{this.getClass().getSimpleName()}), title);        
        if (!localTitle.equals(Language.NOT_FOUND)) {
            this.title = localTitle;
        } else {
            this.title = title;
        }
        if (this instanceof Catalog) {
            PID = this.getClass().getCanonicalName();
        } else {
            PID = this.title;
        }
        this.icon  = icon;
        this.hint  = hint;
        this.model = new EntityModel(this.getClass(), PID) {
            
            @Override
            public IEditor getEditor(String name) {
                IEditor propEditor = super.getEditor(name);
                if (Entity.this.getParent() != null && ((Entity) Entity.this.getParent()).model.hasProperty(name)) {
                    EntityModel parent = ((Entity) Entity.this.getParent()).model;
                    propEditor.addCommand(new SwitchInheritance(
                            getProperty(name), parent.getProperty(name)
                    ));
                    parent.getProperty(name).addChangeListener((n, oldValue, newValue) -> {
                        if (getProperty(name).isInherited()) {
                            propEditor.getEditor().updateUI();
                        }
                    });
                }
                editors.put(name, propEditor);
                return propEditor;
            }
            
        };
        this.model.addChangeListener(this);
    }
    
    /**
     * Добавление новой команды сущности.
     */
    public final void addCommand(EntityCommand command) {
        commands.add(command);
    }
    
    /**
     * Получение списка имеющихся команд сущности.
     */
    public final List<EntityCommand> getCommands() {
        return new LinkedList<>(commands);
    }
    
    @Override
    public void insert(INode child) {
        super.insert(child);
        if (child instanceof Entity) {
            Entity entity = (Entity) child;
            
            SwingUtilities.invokeLater(() -> {
                List<String> inheritance = entity.model.getProperties(Access.Edit)
                    .stream()
                    .filter(propName -> this.model.hasProperty(propName))
                    .collect(Collectors.toList());
                if (!inheritance.isEmpty()) {
                    Logger.getLogger().debug(
                            "Properties ''{0}/@{1}'' has possibility of inheritance", 
                            child, inheritance
                    );
                    inheritance.forEach((propName) -> {
                        entity.model.getProperty(propName).setInherited(this.model.getProperty(propName));
                    });
                }
            });
        }
    }
    
    public String getPID() {
        return model.getPID();
    }
    
    /**
     * Возвращает иконку сущности.
     */
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
    public final String toString() {
        return title;
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
        if (editorPresentation == null) {
            editorPresentation = new EditorPresentation(this);
        }
        return editorPresentation;
    };
    
    public final List<String> getInvalidProperties() {
        stopEditing();
        return editors.entrySet().stream()
                .filter((entry) -> {
                    return !entry.getValue().stopEditing();
                }).map((entry) -> {
                    return entry.getKey();
                }).collect(Collectors.toList());
    }

    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        if (getInvalidProperties().contains(name)) {
            Logger.getLogger().warn(
                    "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}'' (invalid value)", 
                    this, name, oldValue, newValue
            );
        } else {
            Logger.getLogger().debug(
                    "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}''", 
                    this, name, oldValue, newValue
            );
        }
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
            MessageBox msgBox = new MessageBox(
                    MessageType.WARNING, null,
                    MessageFormat.format(Language.get("error@invalidprop"), String.join("\n", invalidProps)),
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent event) {
                            editors.get(getInvalidProperties().get(0)).getFocusTarget().requestFocus();
                        }
                    }
            );
            msgBox.setVisible(true);
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
            MessageBox msgBox = new MessageBox(MessageType.CONFIRMATION, null, Language.get("error@unsavedprop"), new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (e.getID() == Dialog.OK) {
                        model.commit();
                    }
                }
            });
            msgBox.setVisible(true);
        }
        return !model.hasChanges();
    };
    
    public final void stopEditing() {
        editors.values().stream().forEach((editor) -> {
            editor.stopEditing();
        });
    }
    
    public static Entity newInstance(Class entityClass, String title) {
        try {
            return (Entity) entityClass.getConstructor(String.class).newInstance((Object) title);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            Logger.getLogger().error(
                    MessageFormat.format("Unable instantiate entity ''{0}''", entityClass.getCanonicalName()), e
            );
            return null;
        } catch (NoSuchMethodException e) {
            Logger.getLogger().error("Entity ''{0}'' does not have universal constructor (String)", entityClass.getCanonicalName());
            return null;
        }
    }
    
}
