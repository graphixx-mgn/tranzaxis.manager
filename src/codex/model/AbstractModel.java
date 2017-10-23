package codex.model;

import codex.property.PropertyHolder;
import codex.type.IComplexType;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Абстрактная модель объекта. Хранит список его свойств.
 * @see PropertyHolder
 */
public class AbstractModel {
   
    final Map<String, PropertyHolder> properties = new LinkedHashMap<>();
    final Map<String, Access> restrictions = new LinkedHashMap<>();
    
    /**
     * Возвращает признак что модель корректна.
     */
    public final boolean isValid() {
        boolean isValid = true;
        for (PropertyHolder propHolder : getProperties(Access.Any)) {
            isValid = isValid & propHolder.isValid();
        }
        return isValid;
    };
    
    /**
     * Добавление нового свойства в модель.
     * @param propHolder Ссылка на свойство.
     * @param restriction Ограничение видимости свойства в редакторе и/или 
     * селекторе.
     * <pre>
     *  // Create 'hidden' property
     *  addProperty(new PropertyHolder(String.class, "svnUrl", "SVN url", "svn://demo.org/sources", true), Access.Any);
     * </pre>
     */
    public void addProperty(PropertyHolder propHolder, Access restriction) {
        final String propName = propHolder.getName();
        if (properties.containsKey(propName)) {
            throw new IllegalStateException(
                    MessageFormat.format("Model already has property ''{0}''", propName)
            );
        }
        properties.put(propName, propHolder);
        restrictions.put(propName, restriction);
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
    
    /**
     * Получить список свойств модели.
     * @param grant Указатель на уровень доступности. Чтобы выбрать все свойства
     * следует указать {@link Access#Any}.
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
     * Получить значение свойства (объект {@link IComplexType}) по его имени.
     */
    public Object getValue(String name) {
        return getProperty(name).getPropValue();
    }
    
}
