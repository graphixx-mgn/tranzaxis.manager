package codex.model;

import codex.log.Logger;
import codex.property.PropertyChangeListener;
import codex.property.PropertyHolder;
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
   
    final Map<String, PropertyHolder> properties = new LinkedHashMap<>();
    final Map<String, Access> restrictions = new LinkedHashMap<>();
    
    public AbstractModel(String title) {
        addProperty(
                new PropertyHolder(String.class, KEY, "Title", title, null, true), 
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
        if (properties.containsKey(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propName)
            );
        }
        properties.put(propName, propHolder);
        restrictions.put(propName, restriction);
        propHolder.addChangeListener(this);
    }
    
    /**
     * Get reference to property by its name (ID).
     * @param name Short string ID of the property.
     * @return Instance of {@link PropertyHolder}
     */
    public final PropertyHolder getProperty(String name) {
        if (!properties.containsKey(name)) {
            throw new NoSuchFieldError(
                    MessageFormat.format("Model does not have property ''{0}''", name)
            );
        }
        return properties.get(name);
    }
    
    /**
     * Get list of object's properties filtered by access rights.
     * @param grant Access level to find properties. In order to select
     * all properties use {@link Access#Any}.
     * @return List of properties available for the level.
     */
    public final List<PropertyHolder> getProperties(Access grant) {
        return properties.values().stream().filter(new Predicate<PropertyHolder>() {
            @Override
            public boolean test(PropertyHolder propHolder) {
                Access propRestriction = restrictions.get(propHolder.getName());
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
     * @param name Name of property has been changed.
     * @param oldValue Value before modification.
     * @param newValue Value after modification.
     */
    @Override
    public void propertyChange(String name, Object oldValue, Object newValue) {
        Logger.getLogger().debug(
                "Property ''{0}@{1}'' has been changed: ''{2}'' -> ''{3}''", 
                this, name, oldValue, newValue
        );
    }
    
}
