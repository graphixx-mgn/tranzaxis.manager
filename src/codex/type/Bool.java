package codex.type;

import codex.editor.BoolEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.property.PropertyHolder;
import java.text.MessageFormat;

/**
 * Тип-обертка {@link IComplexType} для класса Boolean.
 */
public class Bool implements IComplexType<Boolean, IMask<Boolean>> {

    private final static IEditorFactory EDITOR_FACTORY = (PropertyHolder propHolder) -> {
        return new BoolEditor(propHolder);
    };
    
    private Boolean value;
    
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
    public IEditorFactory editorFactory() {
        return EDITOR_FACTORY;
    }
    
    @Override
    public String toString() {
        return value == true ? "1" : "0";
    }
    
    @Override
    public void valueOf(String value) {
        setValue("1".equals(value));
    }
    
    @Override
    public String getQualifiedValue(Boolean val) {
        return val == null ? "<NULL>" : MessageFormat.format("<{0}>", val == true ? "TRUE" : "FALSE");
    }
    
}
