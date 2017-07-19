package codex.model;

import codex.log.Logger;
import codex.property.PropertyHolder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Class implements high-level term 'object' as a container of properties.
 * @see PropertyHolder
 * @author Gredyaev Ivan
 */
public class AbstractModel implements PropertyChangeListener {
    
    private final String KEY = this.getClass().getCanonicalName()+"@Title";
    
    final Map<String, PropertyHolder> propHolders = new LinkedHashMap<>();
    final Map<String, Access>         propRestrictions = new LinkedHashMap<>();
    
    public AbstractModel(String title) {
        addProperty(
                new PropertyHolder(String.class, KEY, "Title", title, true), 
                Access.Any
        );
    }
    
    /**
     * Add new property to the object. 
     * @param propHolder Reference to property.
     * @param restriction Set restriction to show property value in selector and/or editor.
     * <pre>
     *  // Create 'hidden' property
     *  addProperty(new PropertyHolder(String.class, "svnUrl", "SVN url", "svn://demo.org/sources", true), Access.Any);
     * </pre>
     */
    public final void addProperty(PropertyHolder propHolder, Access restriction) {
        final String propName = propHolder.getName();
        if (propHolders.containsKey(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propName)
            );
        }
        propHolders.put(propName, propHolder);
        propRestrictions.put(propName, restriction);
        propHolder.addPropertyChangeListener(this);
    }
    
    /**
     * Get reference to property by its name (ID).
     * @param name Short string ID of the property.
     * @return Instance of {@link PropertyHolder}
     */
    public final PropertyHolder getProperty(String name) {
        if (!propHolders.containsKey(name)) {
            throw new NoSuchFieldError(
                    MessageFormat.format("Model does not have property ''{0}''", name)
            );
        }
        return propHolders.get(name);
    }
    
    /**
     * Get list of object's properties filtered by access rights.
     * @param grant Access level to find properties. In order to select
     * all properties use {@link Access#Any}.
     * @return List of properties available for the level.
     */
    public final List<PropertyHolder> getProperties(Access grant) {
        return propHolders.values().stream().filter(new Predicate<PropertyHolder>() {
            @Override
            public boolean test(PropertyHolder propHolder) {
                Access propRestriction = propRestrictions.get(propHolder.getName());
                return (propRestriction != grant && propRestriction != Access.Any)  || grant == Access.Any;
            }
        }).collect(Collectors.toList());
    }
    
    /**
     * Get property value by its name (ID).
     * @param name Short string ID of the property.
     * @return Property value.
     */
    public Object getValue(String name) {
        return getProperty(name).getValue();
    }
    
    /**
     * Returns model string representation.
     * @return Title of model.
     */
    @Override
    public final String toString() {
        return getProperty(KEY).toString();
    }

    /**
     * Called by event PropertyChange of {@link PropertyHolder} in case its value
     * has been changed. The listener is being assigning after initial assignment
     * of property value in order to filter fake calls.
     * @param pce Contains information about changed property (name, old value, new value).
     */
    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        Logger.getLogger().debug(
                "Property ''{0}'' has been changed: ''{1}'' -> ''{2}''", 
                pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()
        );
    }
    
}
