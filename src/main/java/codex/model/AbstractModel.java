package codex.model;

import codex.editor.IEditor;
import codex.property.PropertyHolder;
import codex.property.PropertyState;
import codex.type.IComplexType;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Абстрактная модель объекта. Хранит список его свойств.
 * @see PropertyHolder
 */
public class AbstractModel {
   
    final Map<String, PropertyHolder> properties = new LinkedHashMap<>();
    final Map<String, IEditor> editors = new HashMap<>();
    final Map<String, Access>  restrictions = new HashMap<>();
    final Map<String, String>  propertyGroups = new HashMap<>();
    
    /**
     * Возвращает признак что модель корректна.
     */
    public boolean isValid() {
        boolean isValid = true;
        for (String propName : getProperties(Access.Any)) {
            isValid = isValid & getProperty(propName).isValid();
        }
        return isValid;
    };
    
    /**
     * Возвращает состояние свойства.
     */
    public PropertyState getPropState(String name) {
        return getProperty(name).isValid() ? PropertyState.Good : PropertyState.Error;
    }
    
    void addProperty(String name, IComplexType value, boolean require, Access restriction) {
        this.addProperty(new PropertyHolder(name, value, require), restriction);
    }
    
    /**
     * Добавление нового свойства в модель.
     * @param propHolder Ссылка на свойство.
     * @param restriction Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     * <pre>
     *  // Create 'hidden' property
     *  addProperty("svnUrl", new Str("svn://demo.org/sources"), true, Access.Any);
     * </pre>
     */
    protected void addProperty(PropertyHolder propHolder, Access restriction) {
        if (properties.containsKey(propHolder.getName())) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propHolder.getName())
            );
        }
        properties.put(propHolder.getName(), propHolder);
        restrictions.put(propHolder.getName(), restriction);
    }
    
    /**
     * Проверка существования свойства у модели по имени.
     */
    public final boolean hasProperty(String name) {
        return properties.containsKey(name);
    }
    
    /**
     * Получить свойство по его имени.
     */
    public final PropertyHolder getProperty(String name) {
        if (!properties.containsKey(name)) {
            throw new NoSuchFieldError(
                    MessageFormat.format("Model does not have property ''{0}''", name)
            );
        }
        return properties.get(name);
    }
    
    public final void addPropertyGroup(String groupTitle, String... propNames) {
        for (String propName : propNames) {
            if (!properties.containsKey(propName)) {
                throw new NoSuchFieldError(
                        MessageFormat.format("Model does not have property ''{0}''", propName)
                );
            }
            propertyGroups.put(propName, groupTitle);
        }
    }
    
    public final String getPropertyGroup(String propName) {
        return propertyGroups.get(propName);
    }
    
    /**
     * Получить список свойств модели.
     * @param grant Указатель на уровень доступности. Чтобы выбрать все свойства
     * следует указать {@link Access#Any}.
     */
    public final List<String> getProperties(Access grant) {
        return properties.values()
                .stream()
                .filter((PropertyHolder propHolder) -> {
                    Access propRestriction = restrictions.get(propHolder.getName());
                    return (propRestriction != grant && propRestriction != Access.Any)  || grant == Access.Any;
                })
                .map((propHolder) -> {
                    return propHolder.getName();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Установить значение свойства по его имени.
     */
    public void setValue(String name, Object value) {
        getProperty(name).setValue(value);
    }
    
    /**
     * Получить значение свойства по его имени.
     */
    public Object getValue(String name) {
        return getProperty(name).getPropValue().getValue();
    }
    
    public IEditor getEditor(String name) {
        if (!editors.containsKey(name)) {
            editors.put(name, properties.get(name).getPropValue().editorFactory().newInstance(properties.get(name)));
        }
        return editors.get(name);
    }
    
}
