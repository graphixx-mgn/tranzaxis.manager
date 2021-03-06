package codex.type;

import codex.editor.BoolEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Тип-обертка {@link IComplexType} для класса Boolean.
 */
public class Bool implements ISerializableType<Boolean, IMask<Boolean>> {

    private final static IEditorFactory<Bool, Boolean> EDITOR_FACTORY = BoolEditor::new;
    
    private Boolean value;

    public Bool() {
        this(null);
    }
    
    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public Bool(Boolean value) {
        this.value = value;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void setValue(Boolean value) {
        this.value = value;
    }

    @Override
    public IEditorFactory<? extends IComplexType<Boolean, IMask<Boolean>>, Boolean> editorFactory() {
        return EDITOR_FACTORY;
    }

    @Override
    public boolean equals(Object obj) {
        IComplexType complex = (IComplexType) obj;
        return (complex.getValue() == null ? getValue() == null : complex.getValue().equals(getValue()));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.value);
        return hash;
    }
    
    @Override
    public String toString() {
        return value ? "1" : "0";
    }
    
    @Override
    public void valueOf(String value) {
        setValue("1".equals(value));
    }
    
    @Override
    public String getQualifiedValue(Boolean val) {
        return val == null ? "<NULL>" : MessageFormat.format("<{0}>", val ? "TRUE" : "FALSE");
    }
    
}
