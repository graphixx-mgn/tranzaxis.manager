package codex.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class Language {
    
    private static final Map<String, ResourceBundle> bundles = new HashMap<>();
    
    public static String get(String key) {
        String className = new Exception().getStackTrace()[1].getClassName().replaceAll(".*\\.(\\w+)", "$1");
        ResourceBundle bundle;
        if (bundles.containsKey(className)) {
            bundle = bundles.get(className);
        } else {
            bundle = ResourceBundle.getBundle(
                "resource/locale/"+className, 
                new Locale(System.getProperty("user.language"), System.getProperty("user.country"))
            );
            bundles.put(className, bundle);
        }
        return bundle.getString(key);
    }
    
}
