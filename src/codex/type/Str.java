package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StrEditor;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для класса String.
 * @param <String> Базовый Java класс: String.
 */
public class Str<String> implements IComplexType<String> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new StrEditor(propHolder);
    };
    
    private String value = null;
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Str(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
}
