package codex.type;

import codex.editor.EnumEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Тип-обертка {@link IComplexType} для перечислений Enum.
 */
public class Enum implements IComplexType<java.lang.Enum, IMask<java.lang.Enum>> {
    
    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new EnumEditor(propHolder);
    };
    
    private java.lang.Enum value = null;
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Enum(java.lang.Enum value) {
        if (value == null) {
            throw new IllegalStateException(MessageFormat.format("Type ''{0}'' does not support NULL value", this.getClass().getName()));
        }
        this.value = value;
    }

    @Override
    public java.lang.Enum getValue() {
        return value;
    }

    @Override
    public void setValue(java.lang.Enum value) {
        if (value == null) {
            throw new IllegalStateException(MessageFormat.format("Type ''{0}'' does not support NULL value", this.getClass().getName()));
        }
        this.value = value;
    }

    @Override
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return complex.getValue().equals(getValue());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.value);
        return hash;
    }
    
    @Override
    public String toString() {
        return value.name();
    }
    
    @Override
    public void valueOf(String value) {
        setValue(java.lang.Enum.valueOf(this.value.getClass(), value));
    }
    
    @Override
    public String getQualifiedValue(java.lang.Enum val) {
        return val.name();
    }

}
