package codex.utils;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.model.Access;
import codex.model.EntityDefinition;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.property.PropertyHolder;
import codex.service.AbstractService;
import codex.service.IService;
import codex.service.Service;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Iconified;
import com.github.plural4j.Plural;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.IOUtils;
import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Класс утилит для работы с локализующими ресурсами.
 */
@ThreadSafe
public class Language {
    
    public  static final String NOT_FOUND  = "<not defined>";
    public  static final Locale DEF_LOCALE = SupportedLang.English.locale;
    private static final List<Class> EXCLUDES = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Получить строку по ключу. Класс-владелец определяется по стеку вызовов.
     */
    public static String get(String key) {
        List<Class> stack = Caller.getInstance().getClassStack().stream()
                .filter(aClass -> aClass != Language.class)
                .collect(Collectors.toList());
        return getValue(stack.get(0), key, getLocale());
    }
    
    /**
     * Получить строку по имени класса-владельца и ключу.
     */
    public static String get(Class callerClass, String key) {
        return getValue(callerClass, key, getLocale());
    }
    
    public static String get(Class callerClass, String key, Locale locale) {
        return getValue(callerClass, key, locale);
    }

    private static String getValue(Class callerClass, String key, Locale locale) {
        try {
            return BundleCache.getBundle(callerClass, locale).getString(key);
        } catch (Exception e) {
            return NOT_FOUND;
        }
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
                    .filter(aClass -> aClass != Language.class || !EXCLUDES.contains(aClass))
                    .collect(Collectors.toList());
            for (Class callerClass : stack) {
                try {
                    return BundleCache.getBundle(callerClass, getLocale()).getString(key);
                } catch (Exception e) {
                    EXCLUDES.add(callerClass);
                }
            }
            return NOT_FOUND;
        }
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


    private static class BundleCache {
        private static final Object lock = new Object();
        private static final Map<Class, LocaleBundle> CACHE = new HashMap<>();

        private static ResourceBundle getBundle(Class clazz, Locale locale) {
            synchronized (lock) {
                if (CACHE.containsKey(clazz)) {
                    return CACHE.get(clazz).getBundle(locale);
                } else {
                    LocaleBundle bundle = new LocaleBundle(clazz);
                    CACHE.put(clazz, bundle);
                    return bundle.getBundle(locale);
                }
            }
        }

        static String getClassName(Class<?> clazz) {
            return clazz.getTypeName().replaceAll(".*[.$](\\w+)", "$1");
        }
    }


    private static class LocaleBundle {

        private final Class  clazz;
        private final String className;
        private final Map<Locale, ResourceBundle> bundles = new HashMap<>();

        LocaleBundle(Class clazz) {
            this.clazz = clazz;
            this.className = BundleCache.getClassName(clazz);

            for (SupportedLang lang : SupportedLang.values()) {
                bundles.put(lang.getLocale(), loadBundle(lang.getLocale()));
            }
        }

        ResourceBundle getBundle(Locale locale) {
            return bundles.get(locale);
        }

        private ResourceBundle loadBundle(Locale locale) {
            return ResourceBundle.getBundle(
                    "locale/".concat(className),
                    locale,
                    clazz.getClassLoader()
            );
        }
    }


    interface ITranslateService extends IService {}


    public static class TranslateService extends AbstractService<TranslateServiceOptions> implements ITranslateService {

        static {
            Locale osLocale = new Locale(
                    System.getProperty("user.language"),
                    System.getProperty("user.country")
            );
            String langAsStr = Service.getProperty(TranslateService.class, TranslateServiceOptions.PROP_GUI_LANG);
            if (langAsStr != null) {
                SupportedLang language = SupportedLang.valueOf(langAsStr);
                Locale.setDefault(language.getLocale());
            }
            java.util.Locale uiLocale = Language.getLocale();
            if (!uiLocale.equals(osLocale)) {
                Logger.getLogger().debug("" +
                                "OS locale: Language: {0}, Country: {1}",
                        osLocale.getDisplayLanguage(),
                        osLocale.getDisplayCountry()
                );
            }
            Logger.getLogger().debug("" +
                            "UI locale: Language: {0}, Country: {1}",
                    uiLocale.getDisplayLanguage(),
                    uiLocale.getDisplayCountry()
            );
        }

        @Override
        public String getTitle() {
            return "Interface Translation Service";
        }
    }

    @EntityDefinition(icon = "/images/language.png")
    public static class TranslateServiceOptions extends Service<TranslateService> {
        final static String PROP_GUI_LANG = "guiLang";

        public TranslateServiceOptions(EntityRef owner, String title) {
            super(owner, title);
            model.addUserProp(PROP_GUI_LANG, new Enum<>(SupportedLang.valueOf(Language.getLocale())), false, Access.Select);

            model.addModelListener(new IModelListener() {
                @Override
                public void modelSaved(EntityModel model, List<String> changes) {
                    if (changes.contains(PROP_GUI_LANG)) {
                        SwingUtilities.invokeLater(() -> MessageBox.show(
                                MessageType.INFORMATION,
                                Language.get(TranslateServiceOptions.class, "guiLang.notify")
                        ));
                    }
                }
            });
        }
    }


    enum SupportedLang implements Iconified {

        Russian("Русский", ImageUtils.getByPath("/images/rus.png"), new java.util.Locale("ru", "RU")),
        English("English", ImageUtils.getByPath("/images/eng.png"), new java.util.Locale("en", "US"));

        private final String    title;
        private final ImageIcon icon;
        private final java.util.Locale locale;

        SupportedLang(String title, ImageIcon icon, java.util.Locale locale) {
            this.title  = title;
            this.icon   = icon;
            this.locale = locale;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }

        public java.util.Locale getLocale() {
            return locale;
        }

        @Override
        public String toString() {
            return title;
        }

        public static SupportedLang valueOf(java.util.Locale locale) {
            for (SupportedLang lang : SupportedLang.class.getEnumConstants()) {
                if (lang.locale.getLanguage().equals(locale.getLanguage())) {
                    return lang;
                }
            }
            return English;
        }

    }
}
