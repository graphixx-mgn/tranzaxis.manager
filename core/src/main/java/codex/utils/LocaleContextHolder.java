package codex.utils;

import java.util.Locale;

public final class LocaleContextHolder {

    private static final ThreadLocal<Locale> LOCALE_THREAD_LOCAL = new  ThreadLocal<>();

    public static Locale getLocale() {
        return LOCALE_THREAD_LOCAL.get() != null ? LOCALE_THREAD_LOCAL.get() : getDefLocale();
    }

    public static void setLocale(Locale locale) {
        LOCALE_THREAD_LOCAL.set(locale);
    }

    private static String getLanguage() {
        return System.getProperty("user.language");
    }

    private static Locale getDefLocale() {
        final String userLang = getLanguage();
        for (Language.SupportedLang language : Language.SupportedLang.values()) {
            if (language.getLocale().getLanguage().equals(userLang)) {
                return language.getLocale();
            }
        }
        return Language.DEF_LOCALE;
    }

}
