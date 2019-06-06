package codex.utils;

import java.util.Locale;

public final class LocaleContextHolder {

    private static final ThreadLocal<Locale> threadLocalScope = new  ThreadLocal<>();

    public static Locale getLocale() {
        return threadLocalScope.get() != null ? threadLocalScope.get() : Locale.getDefault();
    }

    public static void setLocale(Locale locale) {
        threadLocalScope.set(locale);
    }

}
