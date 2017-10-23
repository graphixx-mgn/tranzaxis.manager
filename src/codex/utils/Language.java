package codex.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Класс утилит для работы с локализующими ресурсами.
 */
public class Language {
    
    private static final Map<String, ResourceBundle> bundles = new HashMap<>();
    private static final ClassLoader LOADER = ClassLoader.getSystemClassLoader();
    
    /**
     * Получить строку по ключу. Класс-владелец определяется по стеку вызовов.
     */
    public static String get(String key) {
        String className = new Exception().getStackTrace()[1].getClassName().replaceAll(".*[\\.\\$](\\w+)", "$1");
        return get(className, key);
    }
    
    /**
     * Получить строку по имени класса-владельца и ключу.
     */
    public static String get(String className, String key) {
        ResourceBundle bundle;
        if (bundles.containsKey(className)) {
            bundle = bundles.get(className);
        } else {
            bundle = ResourceBundle.getBundle("resource/locale/"+className, getLocale());
            bundles.put(className, bundle);
        }
        return bundle.containsKey(key) ? bundle.getString(key) : "<not defined>";
    }
    
    /**
     * Поиск строки в списке классов.
     */
    public static String lookup(List<String> classNames, String key) {
        for (String className : classNames) {
            if (bundles.containsKey(className)) {
                return get(className, key);
            } else if (LOADER.getResource("resource/locale/"+className+"_"+getLocale().toString()+".properties") != null) {
                return get(className, key);
            }
        }
        return "<not defined>";
    }
    
    private static Locale getLocale() {
        return new Locale(System.getProperty("user.language"), System.getProperty("user.country"));
    }
    
}
