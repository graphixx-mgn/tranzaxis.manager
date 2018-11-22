package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StrEditor;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Тип-обертка {@link IComplexType} для класса String.
 */
public class Str implements IComplexType<String, IMask<String>> {
    
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
        this.mask  = (String text) -> true;
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
    @Override
    public IComplexType setMask(IMask<String> mask) {
        this.mask = mask;
        return this;
    }
    
    /**
     * Возвращает маску значения.
     */
    @Override
    public IMask<String> getMask() {
        return mask;
    }

    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return (complex.getValue() == null ? getValue() == null : complex.getValue().equals(getValue()));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.value);
        return hash;
    }
    
    @Override
    public String toString() {
        return IComplexType.coalesce(value, "");
    }
    
    @Override
    public void valueOf(String value) {
        setValue((value == null || value.isEmpty()) ? null : value);
    }

    @Override
    public String getQualifiedValue(String val) {
        return val == null ? "<NULL>" : MessageFormat.format("\"{0}\"", val);
    }
    
}
