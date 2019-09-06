package codex.model;

import codex.utils.Caller;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.prefs.*;

public final class Bootstrap {

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BootProperty {}

    private static Class APP_CLASS;
    static {
        List<Class> classStack = Caller.getInstance().getClassStack();
        APP_CLASS = Caller.getInstance().getClassStack().get(classStack.size() - 1);
    }

    static void setProperty(Class<? extends Entity> entityClass, String PID, String propName, String value) {
        getPreferences(entityClass, PID).put(propName, value);
    }

    public static String getProperty(Class<? extends Entity> entityClass, String PID, String propName) {
        return getPreferences(entityClass, PID).get(propName, "");
    }

    public static Preferences getCatalog(Class<? extends Entity> entityClass, String PID) {
        return getPreferences(entityClass, PID);
    }

    private static Preferences getPreferences(Class<? extends Entity> entityClass, String PID) {
        return Preferences.userRoot().node(APP_CLASS.getSimpleName()).node(entityClass.getTypeName()).node(PID);
    }
}
