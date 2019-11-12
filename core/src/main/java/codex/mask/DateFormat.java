package codex.mask;

public class DateFormat implements IDateMask {

    public static class Full {
        public static IDateMask newInstance() {
            return new DateFormat(Format.Full);
        }
    }

    public static class Time {
        public static IDateMask newInstance() {
            return new DateFormat(Format.Time);
        }
    }

    private final Format format;

    private DateFormat(Format format) {
        this.format = format;
    }

    @Override
    public Format getFormat() {
        return format;
    }
}
