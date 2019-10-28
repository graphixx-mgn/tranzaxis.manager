package codex.type;

import codex.editor.DateTimeEditor;
import codex.editor.IEditorFactory;
import codex.mask.IMask;
import codex.utils.Language;
import java.sql.Time;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class DateTime implements ISerializableType<Date, IMask<Date>> {

    private static final IEditorFactory<DateTime, Date> EDITOR_FACTORY = DateTimeEditor::new;
    private static final String DATETIME_FORMAT = MessageFormat.format(
            "{0} {1}",
            ((SimpleDateFormat) DateFormat.getDateInstance(DateFormat.LONG, Language.getLocale())).toPattern(),
            "HH:mm:ss"
    );
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(DATETIME_FORMAT, Language.getLocale());
    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss");

    public static boolean sameDay(Date date1, Date date2) {
        if (date1 != null && date2 != null) {
            ZoneId zoneId = ZoneId.systemDefault();

            ZonedDateTime zdt1 = ZonedDateTime.ofInstant(date1.toInstant(), zoneId);
            LocalDate ld1 = LocalDate.from(zdt1);

            ZonedDateTime zdt2 = ZonedDateTime.ofInstant(date2.toInstant(), zoneId);
            LocalDate ld2 = LocalDate.from(zdt2);

            return ld1.isEqual(ld2);
        }
        return false;
    }

    public static String format(Date date) {
        return sameDay(new Date(), date) ? TIME_FORMATTER.format(date) : DATE_FORMATTER.format(date);
    }

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
        return val == null ? "<NULL>" : format(val);
    }
}
