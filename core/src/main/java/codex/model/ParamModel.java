package codex.model;

import codex.editor.IEditor;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
import codex.type.IComplexType;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Реализация модели параметров команды.
 */
public final class ParamModel extends AbstractModel implements IPropertyChangeListener {
    
    private final List<IPropertyChangeListener> listeners = new LinkedList<>();
    
    @Override
    public boolean isValid() {
        boolean isValid = true;
        for (String propName : getProperties(Access.Any)) {
            boolean propValid = getProperty(propName).isValid();
            getEditor(propName).setBorder(propValid ? IEditor.BORDER_NORMAL : IEditor.BORDER_ERROR);
            isValid = isValid & propValid;
        }
        return isValid;
    }
    
    public final void addProperty(PropertyHolder propHolder) {
        super.addProperty(propHolder, null);
        getProperty(propHolder.getName()).addChangeListener(this);
    }
    
    public final void addProperty(String name, IComplexType value, boolean require) {
        super.addProperty(name, value, require, null);
        getProperty(name).addChangeListener(this);
    }
    
    public final Map<String, IComplexType> getParameters() {
        Map<String, IComplexType> params = new HashMap<>();
        properties.forEach((name, propHolder) -> params.put(name, propHolder.getPropValue()));
        return params;
    }
    
    public final void addChangeListener(IPropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        new LinkedList<>(listeners).forEach((listener) -> listener.propertyChange(name, oldValue, newValue));
    }
    
}
