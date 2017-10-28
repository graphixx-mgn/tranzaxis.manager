package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StrEditor;
import codex.mask.IMask;
import codex.property.PropertyHolder;

/**
 * Тип-обертка {@link IComplexType} для класса String.
 */
public class Str implements IComplexType<String> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new StrEditor(propHolder);
    };
    
    private String value;
    private IMask<String> mask;
    
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
    public boolean isEmpty() {
        return IComplexType.coalesce(getValue(), "").isEmpty();
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    /**
     * Установить маску значения.
     */
    public IComplexType setMask(IMask<String> mask) {
        this.mask = mask;
        return this;
    }
    
    /**
     * Возвращает маску значения.
     */
    public IMask<String> getMask() {
        return mask;
    }
    
}
