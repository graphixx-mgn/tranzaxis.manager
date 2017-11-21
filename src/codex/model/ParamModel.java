package codex.model;

import codex.property.IPropertyChangeListener;
import codex.type.IComplexType;
import java.util.LinkedList;
import java.util.List;

/**
 * Реализация модели параметров команды.
 */
public final class ParamModel extends AbstractModel implements IPropertyChangeListener {
    
    private final List<IPropertyChangeListener> listeners = new LinkedList<>();
    
    public final void addProperty(String name, IComplexType value, boolean require) {
        super.addProperty(name, value, require, null);
        getProperty(name).addChangeListener(this);
    }
    
    public final void addChangeListener(IPropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        new LinkedList<>(listeners).forEach((listener) -> {
            listener.propertyChange(name, oldValue, newValue);
        });
    }
    
}
