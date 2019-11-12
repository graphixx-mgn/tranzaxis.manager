package codex.mask;

import codex.utils.Language;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public interface IDateMask extends IMask<Date> {

    Format getFormat();

    @Override
    default boolean verify(Date value) {
        return true;
    }

    enum Format {
        Full(
            (SimpleDateFormat) DateFormat.getDateInstance(DateFormat.LONG, Language.getLocale()),
            new SimpleDateFormat(" HH:mm:ss,SSS")
        ),
        Time(
            new SimpleDateFormat(""),
            new SimpleDateFormat("HH:mm:ss")
        );

        private final SimpleDateFormat dateFormat;
        private final SimpleDateFormat timeFormat;

        Format (SimpleDateFormat dateFormat, SimpleDateFormat timeFormat) {
            this.timeFormat = timeFormat;
            this.dateFormat = dateFormat;
        }

        public SimpleDateFormat getDateFormat() {
            return dateFormat;
        }

        public SimpleDateFormat getTimeFormat() {
            return timeFormat;
        }

        public String format(Date date) {
            return MessageFormat.format(
                    "{0}{1}",
                    dateFormat.format(date),
                    timeFormat.format(date)
            );
        }
    }

}
