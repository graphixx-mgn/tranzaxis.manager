package codex.context;

import codex.utils.Caller;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServiceCallContext {

    public synchronized static Class<? extends IContext> getContext() {
        return getContextStream().reduce((first, second) -> second).orElse(null);
    }

    public synchronized static List<Class<? extends IContext>> getContextStack() {
        return getContextStream().collect(Collectors.toList());
    }

    private static Stream<Class<? extends IContext>> getContextStream() {
        return Stream.concat(
                Stream.of(RootContext.class),
                Caller.getInstance().getClassStack().stream()
                        .map(aClass -> {
                            Class<?> parentClass = aClass;
                            while (!IContext.class.isAssignableFrom(parentClass) && parentClass.getEnclosingClass() != null) {
                                parentClass = aClass.getEnclosingClass();
                            }
                            return parentClass;
                        })
                        .filter(IContext.class::isAssignableFrom)
                        .map(aClass -> (Class<? extends IContext>) aClass.asSubclass(IContext.class))
        );
    }

}
