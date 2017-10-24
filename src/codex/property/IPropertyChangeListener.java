package codex.property;

/**
 * Интерфейс слушателя события изменения значения свойства {@link PropertyHolder}.
 */
public interface IPropertyChangeListener {
    
    /**
     * Вызывается при изменении хранимого значения {@link PropertyHolder}.
     * @param name Имя модифицированного свойства.
     * @param oldValue Предыдущее значение.
     * @param newValue Новое значение.
     */
    public void propertyChange(String name, Object oldValue, Object newValue);
    
}
