package codex.service;

import codex.model.Catalog;
import codex.model.Entity;
import codex.utils.ImageUtils;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Stack;

/**
 * Сущность-контейнер настроек сервисов {@link LocalServiceOptions} и
 * производных от него.
 */
public class ServiceCatalog extends Catalog {

    public ServiceCatalog() {
        super(null, ImageUtils.getByPath("/images/services.png"), null, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return LocalServiceOptions.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    // https://habr.com/post/66593/
    @SuppressWarnings("unchecked")
    static <T> Class<? extends T> getServiceConfigClass(Class baseServiceClass, Class serviceClass) {
        final int parameterIndex = 0;
        if (!baseServiceClass.isAssignableFrom(serviceClass.getSuperclass())) {
            throw new IllegalArgumentException("Class " + baseServiceClass.getName() + " is not a superclass of " + serviceClass.getName() + ".");
        }
        Stack<ParameterizedType> genericClasses = new Stack<>();
        Class clazz = serviceClass;

        while (true) {
            Type genericSuperclass = clazz.getGenericSuperclass();
            boolean isParameterizedType = genericSuperclass instanceof ParameterizedType;
            if (isParameterizedType) {
                genericClasses.push((ParameterizedType) genericSuperclass);
            } else {
                genericClasses.clear();
            }
            Type rawType = isParameterizedType ? ((ParameterizedType) genericSuperclass).getRawType() : genericSuperclass;
            if (!baseServiceClass.equals(rawType)) {
                clazz = clazz.getSuperclass();
            } else {
                break;
            }
        }

        Type result = genericClasses.pop().getActualTypeArguments()[parameterIndex];

        while (result instanceof TypeVariable && !genericClasses.empty()) {
            int actualArgumentIndex = getParameterTypeDeclarationIndex((TypeVariable) result);
            ParameterizedType type = genericClasses.pop();
            result = type.getActualTypeArguments()[actualArgumentIndex];
        }

        if (result instanceof TypeVariable) {
            throw new IllegalStateException("Unable to resolve type variable " + result + "." + " Try to replace instances of parametrized class with its non-parameterized subtype.");
        }

        if (result instanceof ParameterizedType) {
            result = ((ParameterizedType) result).getRawType();
        }

        if (result == null) {
            throw new IllegalStateException("Unable to determine actual parameter type for " + serviceClass.getName() + ".");
        }

        if (!(result instanceof Class)) {
            throw new IllegalStateException("Actual parameter type for " + serviceClass.getName() + " is not a Class.");
        }

        return (Class) result;
    }

    private static int getParameterTypeDeclarationIndex(final TypeVariable typeVariable) {
        GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
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
    
}
