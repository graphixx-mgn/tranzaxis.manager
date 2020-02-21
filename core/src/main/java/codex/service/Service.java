package codex.service;

import codex.command.EntityCommand;
import codex.model.*;
import codex.type.*;
import codex.utils.Caller;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@ClassCatalog.Definition(selectorProps = {Service.PROP_VIEW})
public abstract class Service<S extends IService> extends PolyMorph implements ICatalog {

    private final static ImageIcon ICON_STARTED = ImageUtils.getByPath("/images/start.png");
    private final static ImageIcon ICON_STOPPED = ImageUtils.getByPath("/images/stop.png");

    final static String PROP_VIEW = "view";
    private final static String PROP_ENABLED = "enabled";

    private final static String APP_CATALOG = Caller.getInstance().getClassStack().stream().reduce((first, second) -> second).orElse(null).getSimpleName();
    private final static String SRV_CATALOG = "Services";

    private static <S extends IService> void setProperty(Class<S> serviceClass, String propName, String value) {
        getPreferences(serviceClass).put(propName, value);
    }

    public static <S extends IService> String getProperty(Class<S> serviceClass, String propName) {
        return getPreferences(serviceClass).get(propName, null);
    }

    private static <S extends IService> void dropProperty(Class<S> serviceClass, String propName) {
        getPreferences(serviceClass).remove(propName);
    }

    private static <S extends IService> List<String> getProperties(Class<S> serviceClass) {
        try {
            return Arrays.asList(getPreferences(serviceClass).keys());
        } catch (BackingStoreException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static <S extends IService> Preferences getPreferences(Class<S> serviceClass) {
        return Preferences.userRoot()
                .node(APP_CATALOG)
                .node(SRV_CATALOG)
                .node(IService.getServiceInterface(serviceClass).getTypeName());
    }

    private volatile S service;

    public Service(EntityRef owner, String title) {
        super(owner, title);

        // Properties
        model.addDynamicProp(PROP_VIEW, new AnyType(), Access.Edit, () -> new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return isEnabled() ? Service.this.getIcon() : ImageUtils.combine(
                        ImageUtils.grayscale(Service.this.getIcon()),
                        ImageUtils.resize(ICON_STOPPED, 20, 20),
                        SwingConstants.SOUTH_EAST
                );
            }
            @Override
            public String toString() {
                return Service.this.getService().getTitle();
            }
        });
        model.addUserProp(PROP_ENABLED, new Bool(true), false, Access.Any);

        // Property settings
        setPropertyRestriction(EntityModel.THIS, Access.Any);

        IService.Definition definition = getServiceDefinition();
        if (definition != null && definition.optional()) {
            CommandRegistry.getInstance().registerCommand(getClass(), StartService.class);
            CommandRegistry.getInstance().registerCommand(getClass(), StopService.class);
        }

        model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.stream()
                        .filter(propName -> !EntityModel.SYSPROPS.contains(propName) && !model.isStateProperty(propName))
                        .filter(propName -> !EntityModel.SYSPROPS.contains(propName))
                        .forEach(propName -> setProperty(
                                getService().getClass(),
                                propName,
                                model.getProperty(propName).getOwnPropValue().toString()
                        ));
                getProperties(getService().getClass()).forEach(propName -> {
                    if (!model.hasProperty(propName)) {
                        dropProperty(getService().getClass(), propName);
                    }
                });
            }
        });
    }

    protected void setService(S service) {
        this.service = service;
    }

    protected S getService() {
        return this.service;
    }

    final boolean isEnabled() {
        return model.getValue(PROP_ENABLED) == Boolean.TRUE;
    }

    private void setEnabled(boolean started) {
        model.setValue(PROP_ENABLED, started);
        try {
            model.commit(false);
        } catch (Exception e) {/**/}
    }

    private IService.Definition getServiceDefinition() {
        return ((Class<?>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]).getAnnotation(IService.Definition.class);
    }

    // https://habr.com/post/66593/
    @SuppressWarnings("unchecked")
    static <T> Class<? extends T> getServiceConfigClass(Class<T> baseConfigClass, Class baseServiceClass, Class serviceClass) {
        final int parameterIndex = 0;

        // Прекращаем работу если genericClass не является предком actualClass.
        if (!baseServiceClass.isAssignableFrom(serviceClass.getSuperclass())) {
            throw new IllegalArgumentException("Class " + baseServiceClass.getName() + " is not a superclass of " + serviceClass.getName() + ".");
        }
        // Нам нужно найти класс, для которого непосредственным родителем будет genericClass.
        // Мы будем подниматься вверх по иерархии, пока не найдем интересующий нас класс.
        // В процессе поднятия мы будем сохранять в genericClasses все классы - они нам понадобятся при спуске вниз.

        // Пройденные классы - используются для спуска вниз.
        Stack<ParameterizedType> genericClasses = new Stack<>();

        // clazz - текущий рассматриваемый класс
        Class clazz = serviceClass;

        while (true) {
            Type genericSuperclass = clazz.getGenericSuperclass();
            boolean isParameterizedType = genericSuperclass instanceof ParameterizedType;
            if (isParameterizedType) {
                // Если предок - параметризованный класс, то запоминаем его - возможно он пригодится при спуске вниз.
                genericClasses.push((ParameterizedType) genericSuperclass);
            } else {
                // В иерархии встретился непараметризованный класс. Все ранее сохраненные параметризованные классы будут бесполезны.
                genericClasses.clear();
            }
            // Проверяем, дошли мы до нужного предка или нет.
            Type rawType = isParameterizedType ? ((ParameterizedType) genericSuperclass).getRawType() : genericSuperclass;
            if (!baseServiceClass.equals(rawType)) {
                // genericClass не является непосредственным родителем для текущего класса. Поднимаемся по иерархии дальше.
                clazz = clazz.getSuperclass();
            } else {
                // Мы поднялись до нужного класса. Останавливаемся.
                break;
            }
        }
        // Нужный класс найден. Теперь мы можем узнать, какими типами он параметризован.
        Type result = genericClasses.pop().getActualTypeArguments()[parameterIndex];

        while (result instanceof TypeVariable && !genericClasses.empty()) {
            // Похоже наш параметр задан где-то ниже по иерархии, спускаемся вниз.
            // Получаем индекс параметра в том классе, в котором он задан.
            int actualArgumentIndex = getParameterTypeDeclarationIndex((TypeVariable) result);
            // Берем соответствующий класс, содержащий метаинформацию о нашем параметре.
            ParameterizedType type = genericClasses.pop();
            // Получаем информацию о значении параметра.
            result = type.getActualTypeArguments()[actualArgumentIndex];
        }
        if (result instanceof TypeVariable) {
            // Мы спустились до самого низа, но даже там нужный параметр не имеет явного задания.
            // Следовательно из-за "Type erasure" узнать класс для параметра невозможно.
            throw new IllegalStateException("Unable to resolve type variable " + result + "." + " Try to replace instances of parametrized class with its non-parameterized subtype.");
        }
        if (result instanceof ParameterizedType) {
            // Сам параметр оказался параметризованным. Отбросим информацию о его параметрах, она нам не нужна.
            result = ((ParameterizedType) result).getRawType();
        }
        if (result == null) {
            // Should never happen. :)
            throw new IllegalStateException("Unable to determine actual parameter type for " + serviceClass.getName() + ".");
        }
        if (!(result instanceof Class)) {
            // Похоже, что параметр - массив или что-то еще, что не является классом.
            throw new IllegalStateException("Actual parameter type for " + serviceClass.getName() + " is not a Class.");
        }
        return (Class) result;
    }

    private static int getParameterTypeDeclarationIndex(final TypeVariable typeVariable) {
        GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
        // Ищем наш параметр среди всех параметров того класса, где определен нужный нам параметр.
        TypeVariable[] typeVariables = genericDeclaration.getTypeParameters();
        Integer actualArgumentIndex = null;
        for (int i = 0; i < typeVariables.length; i++) {
            if (typeVariables[i].equals(typeVariable)) {
                actualArgumentIndex = i;
                break;
            }
        }
        if (actualArgumentIndex != null) {
            return actualArgumentIndex;
        } else {
            throw new IllegalStateException("Argument " + typeVariable.toString() + " is not found in " + genericDeclaration.toString() + ".");
        }
    }


    private static class StartService extends EntityCommand<Service> {
        StartService() {
            super(
                    "start",
                    Language.get(Service.class, "start@title"),
                    ICON_STARTED,
                    Language.get(Service.class, "start@title"),
                    (control) -> !control.isEnabled()
            );
        }

        @Override
        public void execute(Service context, java.util.Map<String, IComplexType> params) {
            context.setEnabled(true);
        }
    }

    private static class StopService extends EntityCommand<Service> {
        StopService() {
            super(
                    "stop",
                    Language.get(Service.class, "stop@title"),
                    ICON_STOPPED,
                    Language.get(Service.class, "stop@title"),
                    Service::isEnabled
            );
        }

        @Override
        public void execute(Service context, java.util.Map<String, IComplexType> params) {
            context.setEnabled(false);
        }
    }
}
