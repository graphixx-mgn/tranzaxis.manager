package codex.explorer.tree;

import codex.log.Logger;
import codex.model.Access;
import codex.model.EntityModel;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorPresentation;
import codex.property.PropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.Str;
import codex.utils.Language;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

/**
 * Абстракная сущность, базовый родитель прикладных сущностей приложения.
 * Также является узлом дерева проводника, реализуя интерфейс {@link INode}.
 */
public abstract class Entity extends AbstractNode implements PropertyChangeListener {
    
    private final String KEY = this.getClass().getCanonicalName()+"@Title";
    
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
        this.model = new EntityModel() {
            
            @Override
            public void addProperty(PropertyHolder propHolder, Access restriction) {
                super.addProperty(propHolder, restriction);
                propHolder.addChangeListener(Entity.this);
            }
        };
        this.model.addProperty(
                new PropertyHolder(
                        KEY, 
                        Language.get(Entity.class.getSimpleName(), "KEY.title"), 
                        Language.get(Entity.class.getSimpleName(), "KEY.desc"), 
                        new Str(title), true
                ), Access.Edit, true
        );
    }
    
    @Override
    public final void insert(INode child) {
        super.insert(child);
        if (child instanceof Entity) {
            Entity entity = (Entity) child;
            
            List<String> inheritance = entity.model.getProperties(Access.Edit)
                .stream()
                .filter(prop -> this.model.hasProperty(prop.getName()))
                .map((prop) -> {
                    return prop.getName();
                })
                .collect(Collectors.toList());
            if (!inheritance.isEmpty()) {
                Logger.getLogger().debug(
                        "Properties ''{0}/@{1}'' has possibility of inheritance", 
                        "/" + String.join("/", child
                                .getPath()
                                .stream()
                                .skip(1)
                                .collect(Collectors.toList())
                        ), inheritance
                );
                inheritance.forEach((propName) -> {
                    entity.model.getProperty(propName).setInherited(this.model.getProperty(propName));
                });
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
    
    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        Logger.getLogger().debug(
                "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}''", 
                this, name, oldValue, newValue
        );
    };
    
}
