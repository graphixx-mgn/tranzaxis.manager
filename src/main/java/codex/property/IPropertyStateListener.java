package codex.property;

/**
 * Интерфейс слушателя события изменения состояния свойства {@link PropertyHolder}.
 */
@FunctionalInterface
public interface IPropertyStateListener {
    
    /**
     * Вызывается при изменении состояния свойства.
     * @param name Имя свойства.
     */
    public void propertyStatusChange(String name);
    
}
