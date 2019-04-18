package codex.utils;

import codex.property.PropertyHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Класс утилит для работы с локализующими ресурсами.
 */
public class Language {
    
    public  static final String NOT_FOUND = "<not defined>";
    private static final Map<Class, ResourceBundle> BUNDLES = new HashMap<>();
    private static final List<Class> EXCLUDES = new ArrayList<>();
    
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
        if (BUNDLES.containsKey(callerClass)) {
            bundle = BUNDLES.get(callerClass);
        } else {
            try {
                String className = callerClass.getSimpleName().replaceAll(".*[\\.\\$](\\w+)", "$1");
                bundle = ResourceBundle.getBundle("locale/"+className, locale, classLoader);
                BUNDLES.put(callerClass, bundle);
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
    
    private static final Locale LOCALE = new Locale(System.getProperty("user.language"), System.getProperty("user.country"));
    public static Locale getLocale() {
        return LOCALE;
    }
    
}
