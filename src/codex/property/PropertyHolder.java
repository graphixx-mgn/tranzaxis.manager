package codex.property;

import codex.model.AbstractModel;
import codex.type.AbstractType;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

/** 
 * Class implements high-level term 'property' of an object. It contains minimal piece of
 * information of the property such as type, value, name etc.
 * @see AbstractModel
 * @author Gredyaev Ivan
 */
public class PropertyHolder {
    
    private final Class   type;
    private final String  name;
    private final String  title;
    private final String  desc;
    private final boolean require;
    private Object        value;
    
    private final List<PropertyChangeListener> listeners = new LinkedList<>();
    
    /**
     * Creates new instance
     * @param type Class reference which is being considered as type of the property.
     * @param name Short string ID of the property. Parent object can not have several properties with same ID.
     * @param title Title uses to present property.
     * @param desc Description of the property
     * @param value Instance of class 'type'. 
     * @param require Property can not have empty value.
     */
    public PropertyHolder(Class type, String name, String title, String desc, Object value, boolean require) {
        this.type    = type;
        this.name    = name;
        this.title   = title;
        this.desc    = desc;
        this.require = require;
        
        if (checkValue(value, AbstractType.class.isAssignableFrom(type))) {
            this.value = value;
        }
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
     * @return Instance of class 'type' or NULL.
     */
    public final Object getValue() {
        return value;
    }
    
    /**
     * Sets property value with initial checks of given object. In case type is 
     * instance of {@link AbstractType} it calls methods {@link AbstractType#setValue}
     * and {@link AbstractType#getValue}, i.e. proxies values.
     * @param value New property value
     */
    public final void setValue(Object value) {
        Object  prevValue;
        Object  nextValue;
        boolean isAbstract = AbstractType.class.isAssignableFrom(type);
        boolean isReplaced = this.value != value;
        
        if (checkValue(value, isAbstract)) {
            if (isAbstract) {
                prevValue = this.value == null ? null : ((AbstractType) this.value).getValue();
                if (AbstractType.class.isInstance(value)) {
                    nextValue  = value == null ? null : ((AbstractType) value).getValue();
                    this.value = value;
                } else {
                    nextValue  = value;
                    ((AbstractType) this.value).setValue(value);
                }
            } else {
                prevValue  = this.value;
                nextValue  = value;
                this.value = value;
            }
            if (
                (isReplaced) ||
                (prevValue == null ^ nextValue == null) || 
                (prevValue != null && !prevValue.equals(nextValue))
            ) {
                for (PropertyChangeListener listener : listeners) {
                    listener.propertyChange(name, prevValue, nextValue);
                }
            }
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
    
    /**
     * Checks provided value is acceptable.
     * @param value New value
     * @return True if value is correct. Otherwise {@link IllegalStateException} 
     * will be thrown.
     */
    private boolean checkValue(Object value, boolean isAbstract) {
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
            if (!isAbstract && !type.isInstance(value)) {
                throw new IllegalStateException(
                        MessageFormat.format(
                                "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                value.getClass().getCanonicalName(), type.getCanonicalName()
                        )
                );
            }
        }
        return true;
    }
    
    /**
     * Adds new listener for value changing events
     * @param listener Instance of listener
     * @see PropertyChangeListener
     */
    public final void addChangeListener(PropertyChangeListener listener) {
        this.listeners.add(listener);
    }
    
}
