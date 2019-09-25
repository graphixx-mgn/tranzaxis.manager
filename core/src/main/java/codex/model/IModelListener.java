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
    default void modelChanged(EntityModel model, List<String> changes) {};
    
    /**
     * Вызывается при сохранении изменений модели.
     */
    default void modelSaved(EntityModel model, List<String> changes) {};
    
    /**
     * Вызывается при откате изменений модели.
     */
    default void modelRestored(EntityModel model, List<String> changes) {};
    
    /**
     * Вызывается при удалении модели из БД.
     */
    default void modelDeleted(EntityModel model) {};
    
}
