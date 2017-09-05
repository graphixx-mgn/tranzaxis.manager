package codex.property;

import codex.command.ICommand;
import codex.model.AbstractModel;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import codex.type.IComplexType;
import java.util.LinkedHashMap;
import java.util.Map;

/** 
 * Class implements high-level term 'property' of an object. It contains minimal piece of
 * information of the property such as type, value, name etc.
 * @see AbstractModel
 * @author Gredyaev Ivan
 */
public class PropertyHolder {
    
    private final Class    type;
    private final String   name;
    private final String   title;
    private final String   desc;
    private final boolean  require;
    private Object         value;
    private PropertyHolder override;
    
    private final Map<String, List<PropertyChangeListener>> listeners = new LinkedHashMap<>();
    private final List<ICommand> commands  = new LinkedList<>();
    
    /**
     * Creates new instance. Title and description will be provided from localization bundle.
     * @param type Class reference which is being considered as type of the property.
     * @param name Short string ID of the property. Parent object can not have several properties with same ID.
     * @param value Instance of class 'type'. 
     * @param require Property can not have empty value.
     */
    public PropertyHolder(Class type, String name, Object value, boolean require) {
        String caller = new Exception().getStackTrace()[1].getClassName().replaceAll(".*\\.(\\w+)", "$1");
        
        this.type    = type;
        this.name    = name;
        this.title   = Language.get(caller, name+".title");
        this.desc    = Language.get(caller, name+".desc");
        this.require = require;
        
        if (checkValue(value, IComplexType.class.isAssignableFrom(type))) {
            this.value = value;
        }
    }
    
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
        
        if (checkValue(value, IComplexType.class.isAssignableFrom(type))) {
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
     * Returns description of property.
     * @return Description.
     */
    public final String getDescriprion() { 
        return desc; 
    }
    
    /**
     * Returns property value.
     * @return Instance of class 'type' or NULL.
     */
    public final Object getValue() {
        return override == null ? value : override.getValue();
    }
    
    /**
     * Sets property value with initial checks of given object. In case type is 
     * instance of {@link IComplexType} it calls methods {@link IComplexType#setValue}
     * and {@link IComplexType#getValue}, i.e. proxies values.
     * @param value New property value
     */
    public final void setValue(Object value) {
        Object  prevValue;
        Object  nextValue;
        boolean isAbstract = IComplexType.class.isAssignableFrom(type);
        boolean isReplaced = this.value != value;
        
        if (checkValue(value, isAbstract)) {
            if (isAbstract) {
                prevValue = this.value == null ? null : ((IComplexType) this.value).getValue();
                if (IComplexType.class.isInstance(value)) {
                    nextValue  = value == null ? null : ((IComplexType) value).getValue();
                    this.value = value;
                } else {
                    nextValue  = value;
                    ((IComplexType) this.value).setValue(value);
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
                fireChangeEvent(name, prevValue, nextValue);
            }
        }
    }
    
    /**
     * Returns value string representation.
     * @return 
     */
    @Override
    public final String toString() {
        if (getValue() == null) {
            return "";
        } else {
            return getValue().toString();
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
    
    public boolean isValid() {
        return !(isRequired() && isEmpty());
    }
    
    public final boolean isRequired() {
        return require;
    }
    
    public final boolean isEmpty() {
        if (IComplexType.class.isAssignableFrom(type)) {
            //return ((IComplexType) value).isEmpty();
            throw new UnsupportedOperationException("Not supported yet");
        } else if (type.isEnum()) {
            return false;
        } else {
            return getValue() == null || getValue().toString().isEmpty();
        }
    }
    
    /**
     * Adds new listener for value changing events
     * @param listener Instance of listener
     * @see PropertyChangeListener
     */
    public final void addChangeListener(PropertyChangeListener listener) {
        String target = name;
        if (!listeners.containsKey(target)) {
            listeners.put(target, new LinkedList<>());
        }
        listeners.get(target).add(listener);
    }
    
    public final void addChangeListener(String option, PropertyChangeListener listener) {
        String target = name+"@"+option;
        if (!listeners.containsKey(target)) {
            listeners.put(target, new LinkedList<>());
        }
        listeners.get(target).add(listener);
    }
    
    private void fireChangeEvent(String target, Object prevValue, Object nextValue) {
        if (listeners.containsKey(target)) {
            for (PropertyChangeListener listener : listeners.get(target)) {
                listener.propertyChange(target, prevValue, nextValue);
            }
        }
    }
    
    public PropertyHolder addCommand(ICommand<PropertyHolder> command) {
        commands.add(command);
        command.setContext(this);
        return this;
    }
    
    public List<ICommand> getCommands() {
        return new LinkedList<>(commands);
    }
    
    public void setOverride(PropertyHolder propHolder) {
        Object prevValue = getValue();
        override = propHolder;
        fireChangeEvent(name+"@override", null, null);
        fireChangeEvent(name, prevValue, getValue());
    }
    
    public boolean isOverridden() {
        return override != null;
    }
}
