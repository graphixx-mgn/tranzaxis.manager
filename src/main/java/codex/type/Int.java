package codex.type;

import codex.editor.IEditorFactory;
import codex.editor.IntEditor;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.util.Objects;

/**
 * Тип-обертка {@link IComplexType} для класса Integer.
 */
public class Int implements ISerializableType<Integer, IMask<Integer>> {
    
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
    
    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return (complex.getValue() == null ? getValue() == null : complex.getValue().equals(getValue()));
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.value);
        return hash;
    }
    
    @Override
    public String toString() {
        return IComplexType.coalesce(value, "").toString();
    }
    
    @Override
    public void valueOf(String value) {
        setValue(value == null || value.isEmpty() ? null : Integer.valueOf(value));
    }
    
    @Override
    public String getQualifiedValue(Integer val) {
        return val == null ? "<NULL>" : val.toString();
    }
    
}
