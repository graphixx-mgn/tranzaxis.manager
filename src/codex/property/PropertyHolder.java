package codex.property;

import codex.model.AbstractModel;

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
        this.type      = type;
        this.name      = name;
        this.title     = title;
        this.value     = value;
        this.mandatory = mandatory;
    }
    
    /**
     * Returns short string ID of the property.
     * @return Property ID.
     */
    public final String getName() {
        return name;
    }
    
    /**
     * Returns property value.
     * @return Instance of class 'type'.
     */
    public final Object getValue() {
        return value;
    }
    
}
