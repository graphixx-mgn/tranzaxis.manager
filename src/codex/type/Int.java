package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.IntEditor;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для класса Integer.
 * @param <Integer> Базовый Java класс: Integer.
 */
public class Int<Integer> extends Str<Integer> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new IntEditor(propHolder);
    };
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Int(Integer value) {
        super(value);
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
}
