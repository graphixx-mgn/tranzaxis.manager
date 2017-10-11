package codex.type;

import codex.editor.EnumEditor;
import codex.editor.IEditorFactory;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для перечислений Enum.
 * @param <Integer> Базовый Java класс: Enum.
 */
public class Enum implements IComplexType<java.lang.Enum> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new EnumEditor(propHolder);
    };
    
    private java.lang.Enum value = null;
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Enum(java.lang.Enum value) {
        this.value = value;
    }

    @Override
    public java.lang.Enum getValue() {
        return value;
    }

    @Override
    public void setValue(java.lang.Enum value) {
        this.value = value;
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }

}
