package codex.type;

import codex.editor.BigIntEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;

import java.util.Objects;

public class BigInt implements ISerializableType<Long, IMask<Long>> {

    private final static IEditorFactory<BigInt, Long> EDITOR_FACTORY = BigIntEditor::new;

    private Long value;

    public BigInt() {
        this(null);
    }

    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public BigInt(Long value) {
        this.value = value;
    }

    @Override
    public Long getValue() {
        return value;
    }

    @Override
    public void setValue(Long value) {
        this.value = value;
    }

    @Override
    public IEditorFactory<? extends IComplexType<Long, IMask<Long>>, Long> editorFactory() {
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
        setValue(value == null || value.isEmpty() ? null : Long.valueOf(value));
    }

    @Override
    public String getQualifiedValue(Long val) {
        return val == null ? "<NULL>" : val.toString();
    }
}
