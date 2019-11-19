package codex.model;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр изменений свойст модели сущности. Используется для возможности отката
 * внесенных изменений.
 */
final class UndoRegistry {
    
    private final Map<String, RegistryItem> registry = new ConcurrentHashMap<>();
    
    /**
     * Актуализировать запись об изменении в реестре:
     * * если записи нет - создается.
     * * если запись есть - модифицируется текущее значение.
     * * если текущее значение равно исходному - запись удалается.
     * @param key Имя свойства.
     * @param previousValue Предыдущее значение.
     * @param currentValue Текущуу значение.
     */
    public final void put(String key, Object previousValue, Object currentValue) {
        if (exists(key)) {
            if ((previous(key) == null ? currentValue == null : previous(key).equals(currentValue))) {
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
    public final Object previous(String key) {
        return registry.containsKey(key) ? registry.get(key).previousValue : null;
    }
    
    /**
     * Возвращает текущее значение свойства.
     */
    public final Object current(String key) {
        return registry.get(key).currentValue;
    }
    
    /**
     * Возвращает флаг наличия записи в реестре.
     */
    public final boolean exists(String key) {
        return registry.containsKey(key);
    }
    
    /**
     * Возвращает флаг что реестр пуст.
     */
    public final boolean isEmpty() {
        return registry.keySet().isEmpty();
    }
    
    /**
     * Вставить новую запись о свойстве.
     */
    private void insert(String key, Object previousValue, Object currentValue) {
        registry.put(key, new RegistryItem(previousValue, currentValue));
    }
    
    /**
     * Обновить текущее значение в записи.
     */
    private void update(String key, Object value) {
        registry.get(key).currentValue = value;
    }
    
    /**
     * Удалить запись о свойстве.
     */
    void delete(String key) {
        registry.remove(key);
    }
    
    /**
     * Очистка реестра.
     */
    void clear() {
        registry.clear();
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
