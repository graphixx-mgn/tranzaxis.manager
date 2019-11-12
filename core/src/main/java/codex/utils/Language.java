package codex.utils;

import codex.property.PropertyHolder;
import com.github.plural4j.Plural;
import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс утилит для работы с локализующими ресурсами.
 */
public class Language {
    
    public  static final String NOT_FOUND = "<not defined>";
    private static final Map<Class, ResourceBundle> BUNDLES = new HashMap<>();
    private static final List<Class> EXCLUDES = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Получить строку по ключу. Класс-владелец определяется по стеку вызовов.
     */
    public static String get(String key) {
        List<Class> stack = Caller.getInstance().getClassStack().stream()
                .filter(aClass -> aClass != Language.class)
                .collect(Collectors.toList());
        return getValue(stack.get(0), key, getLocale(), stack.get(0).getClassLoader());
    }
    
    /**
     * Получить строку по имени класса-владельца и ключу.
     */
    public static String get(Class callerClass, String key) {
        return getValue(callerClass, key, getLocale(), callerClass.getClassLoader());
    }
    
    public static String get(Class callerClass, String key, Locale locale) {
        return getValue(callerClass, key, locale, callerClass.getClassLoader());
    }

    private static String getValue(Class callerClass, String key, Locale locale, ClassLoader classLoader) {
        ResourceBundle bundle;
        if (BUNDLES.containsKey(callerClass) && BUNDLES.get(callerClass).getLocale() == locale) {
            bundle = BUNDLES.get(callerClass);
        } else {
            try {
                String className = callerClass.getSimpleName().replaceAll(".*[\\.\\$](\\w+)", "$1");
                bundle = ResourceBundle.getBundle("locale/"+className, locale, classLoader);
                if (!BUNDLES.containsKey(callerClass) && getLocale() == locale) {
                    BUNDLES.put(callerClass, bundle);
                }
            } catch (MissingResourceException e) {
                EXCLUDES.add(callerClass);
                return NOT_FOUND;
            }
        }
        return bundle.containsKey(key) ? bundle.getString(key) : NOT_FOUND;
    }
    
    /**
     * Поиск строки в списке классов. Используется для загрузки названий и описаний
     * свойств {@link PropertyHolder}.
     */
    public static String lookup(String key) {
        if (key == null) {
            return NOT_FOUND;
        } else {
            List<Class> stack = Caller.getInstance().getClassStack().stream()
                    .filter(aClass -> aClass != Language.class)
                    .collect(Collectors.toList());
            try {
                for (Class callerClass : stack) {
                    if (EXCLUDES.contains(callerClass)) {
                        continue;
                    }
                    if (BUNDLES.containsKey(callerClass) && BUNDLES.get(callerClass).containsKey(key)) {
                        return BUNDLES.get(callerClass).getString(key);
                    } else if (callerClass.getClassLoader() != null) {
                        try {
                            String className = callerClass.getSimpleName().replaceAll(".*[\\.\\$](\\w+)", "$1");
                            ResourceBundle bundle = ResourceBundle.getBundle("locale/" + className, getLocale(), callerClass.getClassLoader());
                            if (bundle.containsKey(key)) {
                                BUNDLES.put(callerClass, bundle);
                                return BUNDLES.get(callerClass).getString(key);
                            }
                        } catch (MissingResourceException e) {
                            EXCLUDES.add(callerClass);
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return NOT_FOUND;
    }

    public static Locale getLocale() {
        return LocaleContextHolder.getLocale();
    }

    private final static Map<Locale, Plural.WordForms[]> PLURAL_FORMS = new HashMap<>();
    public static Plural getPlural() {
        if (!PLURAL_FORMS.containsKey(getLocale())) {
            String resourceName = "/locale/Language_"+getLocale()+".properties";
            try (InputStream stream = Language.class.getResourceAsStream(resourceName)) {
                PLURAL_FORMS.put(getLocale(), Plural.parse(
                        String.join("\r\n", IOUtils.readLines(stream, StandardCharsets.UTF_8))
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        switch (getLocale().getLanguage()) {
            case "ru": return new Plural(Plural.RUSSIAN, PLURAL_FORMS.get(getLocale()));
            default:   return new Plural(Plural.ENGLISH, PLURAL_FORMS.get(getLocale()));
        }
    }
    
}
