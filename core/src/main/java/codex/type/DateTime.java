package codex.type;

import codex.editor.DateTimeEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import java.sql.Time;
import java.util.Date;

public class DateTime implements ISerializableType<Date, IMask<Date>> {

    private final static IEditorFactory<DateTime, Date> EDITOR_FACTORY = DateTimeEditor::new;

    private Date value;

    public DateTime() {
        this(null);
    }

    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public DateTime(Time value) {
        this.value = value;
    }

    @Override
    public Date getValue() {
        return value;
    }

    @Override
    public void setValue(Date value) {
        this.value = value;
    }

    @Override
    public IEditorFactory<? extends IComplexType<Date, IMask<Date>>, Date> editorFactory() {
        return EDITOR_FACTORY;
    }

    @Override
    public String toString() {
        return value == null ? "" : String.valueOf(value.getTime());
    }

    @Override
    public void valueOf(String value) {
        setValue(value == null || value.isEmpty() ? null : new Date(Long.valueOf(value)));
    }

    @Override
    public String getQualifiedValue(Date val) {
        return null;
    }
}
