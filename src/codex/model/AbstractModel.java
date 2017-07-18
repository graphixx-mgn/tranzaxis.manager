package codex.model;

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
public class AbstractModel {
    
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
     * @see
     */
    public final void addProperty(PropertyHolder propHolder, Access restriction) {
        final String propName = propHolder.getName();
        if (propHolders.containsKey(propName)) {
            throw new IllegalStateException(MessageFormat.format("Model already has property '{0}'", propName));
        }
        propHolders.put(propName, propHolder);
        propRestrictions.put(propName, restriction);
    }
    
    /**
     * Get reference to property by its name (ID).
     * @param name Short string ID of the property.
     * @return Instance of {@link PropertyHolder}
     */
    public final PropertyHolder getProperty(String name) {
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
    
}
