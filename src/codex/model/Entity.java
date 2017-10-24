package codex.model;

import codex.editor.IEditor;
import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.presentation.SwitchInheritance;
import codex.property.PropertyChangeListener;
import codex.type.Str;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements PropertyChangeListener {
   
    private final ImageIcon icon;
    private final String    title;
    private final String    hint;
    
    private EditorPresentation   editor;
    private SelectorPresentation selector;
    
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
        this.title = title;
        this.icon  = icon;
        this.hint  = hint;
        this.model = new EntityModel() {
            
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
                return propEditor;
            }
            
        };
        
        this.model.addProperty("KEY", new Str(title), true, Access.Edit, true);
        this.model.addChangeListener(this);
    }
    
    @Override
    public final void insert(INode child) {
        super.insert(child);
        if (child instanceof Entity) {
            Entity entity = (Entity) child;

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
        }
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
        if (selector == null) {
            selector = new SelectorPresentation();
        }
        return selector;
    };

    @Override
    public final EditorPresentation getEditorPresentation() {
        if (editor == null) {
            editor = new EditorPresentation(this);
        }
        return editor;
    };

    @Override
    public final void propertyChange(String name, Object oldValue, Object newValue) {
        Logger.getLogger().debug(
                "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}''", 
                this, name, oldValue, newValue
        );
    }
    
}
