package codex.property;

/**
 * Listener interface for changing value of {@link PropertyHolder}.
 * @see PropertyHolder
 * @author Gredyaev Ivan
 */
public interface PropertyChangeListener {
    
    /**
     * Called by event PropertyChange of {@link PropertyHolder} in case its value
     * has been changed. The listener is being assigning after initial assignment
     * of property value in order to filter fake calls.
     * @param name Name of property has been changed.
     * @param oldValue Value before modification.
     * @param newValue Value after modification.
     */
    public void propertyChange(String name, Object oldValue, Object newValue);
    
}
