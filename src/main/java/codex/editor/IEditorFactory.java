package codex.editor;

import codex.mask.IMask;
import codex.property.PropertyHolder;
import codex.type.IComplexType;

/**
 * Интерфейс фабрики генерации редакторов свойств.
 */
@FunctionalInterface
public interface IEditorFactory<T extends IComplexType<V, ? extends IMask<V>>, V> {
    /**
     * Создает редактор для свойства.
     * @param propHolder Редактируемое свойство.
     */
    IEditor<T, V> newInstance(PropertyHolder<T, V> propHolder);
    
}
