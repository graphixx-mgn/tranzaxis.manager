package codex.utils;

import java.util.Locale;

public final class LocaleContextHolder {

    static {
        Locale.setDefault(getDefLocale());
    }

    private static final ThreadLocal<Locale> LOCALE_THREAD_LOCAL = new  ThreadLocal<>();

    public static Locale getLocale() {
        return LOCALE_THREAD_LOCAL.get() != null ? LOCALE_THREAD_LOCAL.get() : Locale.getDefault();
    }

    public static void setLocale(Locale locale) {
        LOCALE_THREAD_LOCAL.set(locale);
    }

    private static Locale getDefLocale() {
        return Language.SupportedLang.valueOf(Locale.getDefault()).getLocale();
    }

}
