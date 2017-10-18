package codex.explorer.tree;

import codex.log.Logger;
import codex.model.Access;
import codex.model.EntityModel;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.PropertyHolder;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode {
    
    final ImageIcon icon;
    final String    title;
    final String    hint;
    
    private EditorPresentation   editor;
    private SelectorPresentation selector;
    
    /**
     * Модель сущности, контейнер всех её свойств.
     */
    public  final EntityModel model;
    
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
        this.model = new EntityModel(title);
    }
    
    @Override
    public final void insert(INode child) {
        super.insert(child);
        if (child instanceof Entity) {
            Entity entity = (Entity) child;
            for (PropertyHolder propHolder : entity.model.getProperties(Access.Edit)) {
                if (this.model.hasProperty(propHolder.getName())) {
                    PropertyHolder parentHolder = this.model.getProperty(propHolder.getName());
                    propHolder.setInherited(parentHolder);
                    Logger.getLogger().debug(
                            "Property ''{0}/@{1}'' has possibility of inheritance", 
                            "/" + String.join("/", child
                                    .getPath()
                                    .stream()
                                    .skip(1)
                                    .collect(Collectors.toList())
                            ), propHolder.getName()
                    );
                }
            }
        }
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
    
}
