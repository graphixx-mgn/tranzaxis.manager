package codex.type;

import codex.editor.DateTimeEditor;
import codex.editor.IEditorFactory;
import codex.mask.DateFormat;
import codex.mask.IDateMask;
import java.sql.Time;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

public class DateTime implements ISerializableType<Date, IDateMask> {

    private static final IEditorFactory<DateTime, Date> EDITOR_FACTORY = DateTimeEditor::new;

    public static Date trunc(Date date) {
        Instant instant = date.toInstant();
        instant = instant.truncatedTo(ChronoUnit.DAYS);
        return Date.from(instant);
    }

    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }

    private Date value;
    private IDateMask mask;

    public DateTime() {
        this(null);
    }

    /**
     * Конструктор типа.
     * @param value Внутреннее хранимое значение.
     */
    public DateTime(Time value) {
        setValue(value);
        setMask(DateFormat.Full.newInstance());
    }

    @Override
    public Date getValue() {
        return value;
    }

    @Override
    public void setValue(Date value) {
        this.value = value == null ? null : new Date(value.getTime()) {
            @Override
            public String toString() {
                return getMask().getFormat().format(this);
            }
        };
    }

    @Override
    public IEditorFactory<DateTime, Date> editorFactory() {
        return EDITOR_FACTORY;
    }

    /**
     * Установить маску значения.
     */
    @Override
    public ISerializableType<Date, IDateMask> setMask(IDateMask mask) {
        this.mask = mask;
        return this;
    }

    /**
     * Возвращает маску значения.
     */
    @Override
    public IDateMask getMask() {
        return mask;
    }

    @Override
    public String toString() {
        return value == null ? "" : String.valueOf(value.getTime());
    }

    @Override
    public void valueOf(String value) {
        setValue(value == null || value.isEmpty() ? null : new Date(Long.parseLong(value)));
    }

    @Override
    public String getQualifiedValue(Date val) {
        return val == null ? "<NULL>" : MessageFormat.format("''{0}''", getMask().getFormat().format(val));
    }
}
