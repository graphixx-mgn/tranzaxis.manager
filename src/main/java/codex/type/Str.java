package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.StrEditor;
import codex.mask.IMask;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Тип-обертка {@link IComplexType} для класса String.
 */
public class Str implements ISerializableType<String, IMask<String>> {
    
    private final static IEditorFactory<Str, String> EDITOR_FACTORY = StrEditor::new;
    
    private String value;
    private IMask<String> mask;

    public Str() {
        this(null);
    }
    
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
    public IEditorFactory<? extends IComplexType<String, IMask<String>>, String> editorFactory() {
        return EDITOR_FACTORY;
    }
    
    /**
     * Установить маску значения.
     */
    @Override
    public ISerializableType<String, IMask<String>> setMask(IMask<String> mask) {
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
