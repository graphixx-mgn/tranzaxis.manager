package codex.model;

import java.util.List;

/**
 * Интерфейс слушателя событий изменения состояния модели с ущности {@link EntityModel}
 */
public interface IModelListener {
    
    /**
     * Вызывается при изменении одного из свойств модели.
     * @param changes Список имен модифицированных свойств.
     */
    default void modelChanged(List<String> changes) {};
    
    /**
     * Вызывается при сохранении изменений модели.
     */
    default void modelSaved(List<String> changes) {};
    
    /**
     * Вызывается при откате изменений модели.
     */
    default void modelRestored(List<String> changes) {};
    
}
