package codex.editor;

import codex.property.PropertyHolder;

/**
 * Интерфейс фабрики генерации редакторов свойств.
 */
@FunctionalInterface
public interface IEditorFactory {
    /**
     * Создает редактор для свойства.
     * @param propHolder Редактируемое свойство.
     */
    public IEditor newInstance(PropertyHolder propHolder);
    
}
