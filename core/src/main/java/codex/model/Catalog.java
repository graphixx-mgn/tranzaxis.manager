package codex.model;

import codex.editor.IEditor;
import codex.type.EntityRef;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.util.function.BiPredicate;

/**
 * Католог сущностей проводника. Основное отличие от {@link Entity} - каталог 
 * должен существовать в дереве в единственном экземпляре.
 */
public abstract class Catalog extends Entity implements ICatalog {

    private static final String    PROP_FILTER = "filter";
    private static final ImageIcon ICON_FILTER = ImageUtils.getByPath("/images/filter.png");

    private Class<? extends Enum>  filterClass = null;

    public Catalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    protected final <E extends Enum<E> & IFilter> void setChildFilter(Class<E> filterClass) {
        this.filterClass = filterClass;
        model.addUserProp(PROP_FILTER, new codex.type.Enum<>(filterClass.getEnumConstants()[0]), true, Access.Any);
        model.getProperty(PROP_FILTER).addChangeListener((name, oldValue, newValue) -> {
            new Thread(() -> {
                try {
                    model.commit(false, PROP_FILTER);
                } catch (Exception ignore) {}
            }).start();
        });
    }

    public final boolean isChildFilterDefined() {
        return filterClass != null;
    }

    @SuppressWarnings("unchecked")
    public final <E extends Enum<E> & IFilter> Class<E> getChildFilter() {
        return (Class<E>) filterClass;
    }

    public final IFilter getCurrentFilter() {
        if (filterClass == null) {
            throw new IllegalStateException("Child filter is not set");
        }
        return (Catalog.IFilter) model.getUnsavedValue(PROP_FILTER);
    }

    public final IEditor getFilterEditor() {
        if (filterClass == null) {
            throw new IllegalStateException("Child filter is not set");
        }
        return model.getEditor(PROP_FILTER);
    }


    public interface IFilter extends Iconified {
        BiPredicate<Entity, Entity> getCondition();

        @Override
        default ImageIcon getIcon() {
            return ICON_FILTER;
        }
    }
}