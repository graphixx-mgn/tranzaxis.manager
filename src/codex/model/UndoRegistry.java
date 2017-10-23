package codex.model;

import codex.log.Logger;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Реестр изменений свойст модели сущности. Используется для возможности отката
 * внесенных изменений.
 */
class UndoRegistry {
    
    private final Map<String, RegistryItem> registry = new HashMap<>();
    
    /**
     * Актуализировать запись об изменении в реестре:
     * * если записи нет - создается.
     * * если запись есть - модифицируется текущее значение.
     * * если текущее значение равно исходному - запись удалается.
     * @param key Имя свойства.
     * @param previousValue Предыдущее значение.
     * @param currentValue Текущуу значение.
     */
    public void put(String key, Object previousValue, Object currentValue) {
        if (exists(key)) {
            if (previous(key).equals(currentValue)) {
                delete(key);
            } else {
                update(key, currentValue);
            }
        } else {
            insert(key, previousValue, currentValue);
        }
    }
    
    /**
     * Возвращает начальное значение свойства.
     */
    public Object previous(String key) {
        return registry.get(key).previousValue;
    }
    
    /**
     * Возвращает текущее значение свойства.
     */
    public Object current(String key) {
        return registry.get(key).currentValue;
    }
    
    /**
     * Возвращает флаг наличия записи в реестре.
     */
    public boolean exists(String key) {
        return registry.containsKey(key);
    }
    
    /**
     * Вставить новую запись о свойстве.
     */
    private void insert(String key, Object previousValue, Object currentValue) {
        registry.put(key, new RegistryItem(previousValue, currentValue));
        Logger.getLogger().debug(
                "Property ''{0}'' inserted to undo registry: {1}", 
                key, registry.get(key)
        );
    }
    
    /**
     * Обновить текущее значение в записи.
     */
    private void update(String key, Object value) {
        registry.get(key).currentValue = value;
        Logger.getLogger().debug(
                "Property ''{0}'' undo registry item altered: {1}", 
                key, registry.get(key)
        );
    }
    
    /**
     * Удалить запись о свойстве.
     */
    private void delete(String key) {
        registry.remove(key);
        Logger.getLogger().debug("Property ''{0}'' dropped from undo registry", key);
    }
    
    private class RegistryItem {
        
        Object previousValue;
        Object currentValue;
        
        public RegistryItem(Object previousValue, Object currentValue) {
            this.previousValue = previousValue;
            this.currentValue  = currentValue;
        }
        
        @Override
        public String toString() {
            return MessageFormat.format("[{0} -> {1}]", previousValue, currentValue);
        }
        
    }
    
}
