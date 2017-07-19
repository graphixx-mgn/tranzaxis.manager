package codex.property;

import codex.model.AbstractModel;
import java.beans.PropertyChangeSupport;
import java.text.MessageFormat;

/** 
 * Class implements high-level term 'property' of an object. It contains minimal piece of
 * information of the property such as type, value, name etc.
 * @see AbstractModel
 * @author Gredyaev Ivan
 */
public class PropertyHolder extends PropertyChangeSupport {
    
    private final Class   type;
    private final String  name;
    private final String  title;
    private final boolean mandatory;
    private Object        value;
    
    /**
     * Creates new instance
     * @param type Class reference which is being considered as type of the property.
     * @param name Short string ID of the property. Parent object can not have several properties with same ID.
     * @param title Title uses to present property.
     * @param value Instance of class 'type'. 
     * @param mandatory Property can not have empty value.
     */
    public PropertyHolder(Class type, String name, String title, Object value, boolean mandatory) {
        super(name);
        
        this.type      = type;
        this.name      = name;
        this.title     = title;
        this.mandatory = mandatory;
        
        setValue(value);
    }
    
    /**
     * Returns type of property.
     * @return Class of property value.
     */
    public final Class getType() {
        return type;
    }
    
    /**
     * Returns short string ID of the property.
     * @return Property ID.
     */
    public final String getName() {
        return name;
    }
    
    /**
     * Returns title of property.
     * @return Title.
     */
    public final String getTitle() { 
        return title; 
    }
    
    /**
     * Returns property value.
     * @return Instance of class 'type'.
     */
    public final Object getValue() {
        return value;
    }
    
    /**
     * Sets property value with initial checks of given object.
     * @param value New property value
     */
    public final void setValue(Object value) {
        boolean hasChanged;
        Object  prevValue = this.value;

        if (value == null) {
            if (type.isEnum()) {
                throw new IllegalStateException(
                        MessageFormat.format(
                                "Invalid value: property type ''{1}'' does not support NULL value", 
                                name, type.getCanonicalName()
                        )
                );
            }
        } else {
            if (!type.isInstance(value)) {
                throw new IllegalStateException(
                        MessageFormat.format(
                                "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                value.getClass().getCanonicalName(), type.getCanonicalName()
                        )
                );
            }
        }
        hasChanged = 
                (this.value == null ^ value == null) || 
                (this.value != null && !this.value.equals(value));
        this.value = value;
        if (hasChanged) {
            firePropertyChange(name, prevValue, value);
        }
    }
    
    /**
     * Returns value string representation.
     * @return 
     */
    @Override
    public final String toString() {
        if (value == null) {
            return "";
        } else {
            return value.toString();
        }
    }
    
}
