package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.IntEditor;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для класса Integer.
 */
public class Int implements IComplexType<Integer> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new IntEditor(propHolder);
    };
    
    private Integer value;
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Int(Integer value) {
        this.value = value;
    }
    
    @Override
    public Integer getValue() {
        return value;
    }

    @Override
    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
}
