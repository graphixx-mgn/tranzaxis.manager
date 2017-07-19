package codex.type;

/**
 * Interface of custom complex type.
 * @author Gredyaev Ivan
 */
public interface AbstractType extends Cloneable {
    
    /**
     * Get actual value of the abstract type up to specific implementation.
     * @return Object
     */
    public Object getValue();
    
    /**
     * Set new value to the abstract type up to specific implementation.
     * @param value New value.
     */
    public void setValue(Object value);
    
}
