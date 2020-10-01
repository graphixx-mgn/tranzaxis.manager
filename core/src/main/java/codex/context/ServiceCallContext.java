package codex.context;

import codex.log.Logger;
import codex.utils.Caller;
import net.jcip.annotations.ThreadSafe;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ThreadSafe
public class ServiceCallContext {

    public synchronized static Class<? extends IContext> getContext() {
        return getContextStream().reduce((first, second) -> second).orElse(null);
    }

    public synchronized static List<Class<? extends IContext>> getContextStack() {
        return getContextStream().collect(Collectors.toList());
    }

    private static Stream<Class<? extends IContext>> getContextStream() {
        List<Class> callStack = Caller.getInstance().getClassStack();
        Collections.reverse(callStack);
        Stream<Class<? extends IContext>> callStream = Stream.concat(
                Stream.of(RootContext.class),
                callStack.stream()
                        .map(aClass -> {
                            Class<?> parentClass = aClass;
                            while (!IContext.class.isAssignableFrom(parentClass) && parentClass.getEnclosingClass() != null) {
                                parentClass = parentClass.getEnclosingClass();
                            }
                            return parentClass;
                        })
                        .filter(IContext.class::isAssignableFrom)
                        .distinct()
                        .map(aClass -> (Class<? extends IContext>) aClass.asSubclass(IContext.class))
        );
        if (Logger.getCallContext() != null) {
            callStream = Stream.concat(callStream, Stream.of(Logger.getCallContext()));
        }
        return callStream;
    }

}
