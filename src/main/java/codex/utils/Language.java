package codex.utils;

import codex.property.PropertyHolder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Класс утилит для работы с локализующими ресурсами.
 */
public class Language {
    
    public  static final String NOT_FOUND = "<not defined>";
    private static final Map<Class, ResourceBundle> bundles = new HashMap<>();
    
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
        if (bundles.containsKey(callerClass)) {
            bundle = bundles.get(callerClass);
        } else {
            String className = callerClass.getSimpleName().replaceAll(".*[\\.\\$](\\w+)", "$1");
            if (classLoader.getResource("locale/"+className+"_"+locale.toString()+".properties") != null) {
                bundle = ResourceBundle.getBundle("locale/"+className, locale, classLoader);
                bundles.put(callerClass, bundle);
            } else {
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
                    String className = callerClass.getSimpleName().replaceAll(".*[\\.\\$](\\w+)", "$1");
                    if (bundles.containsKey(callerClass) && bundles.get(callerClass).containsKey(key)) {
                        return bundles.get(callerClass).getString(key);
                    } else if (callerClass.getClassLoader() != null && callerClass.getClassLoader().getResource("locale/" + className + "_" + getLocale().toString() + ".properties") != null) {
                        ResourceBundle bundle = ResourceBundle.getBundle("locale/" + className, getLocale(), callerClass.getClassLoader());
                        if (bundle.containsKey(key)) {
                            bundles.put(callerClass, bundle);
                            return bundles.get(callerClass).getString(key);
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
        return new Locale(System.getProperty("user.language"), System.getProperty("user.country"));
    }
    
}
