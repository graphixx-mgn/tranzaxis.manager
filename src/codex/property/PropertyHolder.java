package codex.property;

import codex.model.AbstractModel;
import codex.type.AbstractType;
import codex.type.TypeWrapper;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
     * @return Instance of class 'type' or NULL.
     */
    public final Object getValue() {
        boolean isAbstract = AbstractType.class.isAssignableFrom(type);
        if (isAbstract) {
            return value;
        } else {
            return ((AbstractType) value).getValue();
        }
    }
    
    /**
     * Sets property value with initial checks of given object. In case type is 
     * instance of {@link AbstractType} it calls methods {@link AbstractType#setValue}
     * and {@link AbstractType#getValue}, i.e. proxies values.
     * @param value New property value
     */
    public final void setValue(Object value) {
        boolean isAbstract = AbstractType.class.isAssignableFrom(type);
        
        if (value == null && (type.isEnum())) {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "Invalid value: property type ''{1}'' does not support NULL value", 
                            name, type.getCanonicalName()
                    )
            );
        } else if (value != null) {
            if (!isAbstract && !type.isInstance(value)) {
                throw new IllegalStateException(
                        MessageFormat.format(
                                "Invalid value: given ''{0}'' while expecting ''{1}''", 
                                value.getClass().getCanonicalName(), type.getCanonicalName()
                        )
                );
            }
        }
        
        Object prevObject = null;
        if (this.value != null) {
            try {
                prevObject = clone(this.value);
            } catch (ClassNotFoundException | IOException e) {
                throw new ClassCastException("Unable to create copy of value");
            }
        }
        
        boolean newLink  = this.value != value;
        Object prevValue = this.value == null ? null : ((AbstractType) this.value).getValue();
        Object nextValue = value == null ? null : (
                AbstractType.class.isInstance(value) ? ((AbstractType) value).getValue() : value
        );
        
        if (this.value == null) {
            if (isAbstract) {
                this.value = value;
            } else {
                this.value = new TypeWrapper(value);
            }
        } else {
            if (AbstractType.class.isInstance(value)) {
                this.value = value;
            } else {
                ((AbstractType) this.value).setValue(value);
            }
        }
        
        if (
            (newLink) ||
            (prevValue == null ^ nextValue == null) || 
            (prevValue != null && !prevValue.equals(nextValue))
        ) {
            firePropertyChange(name, prevObject, this.value);
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
     * Make copy of value in order to call PropertyChange event.
     * @param value Cloneable value.
     * @return Clone of value.
     * @throws IOException
     * @throws ClassNotFoundException 
     * @see https://habrahabr.ru/post/246993
     */
    private Object clone(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream ous = new ObjectOutputStream(baos)) {
            ous.writeObject(value);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
    
}
