package codex.log;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class LoggerContext {

    private static final ThreadLocal<Stack<Object>> contextList = new  ThreadLocal<>();

    public synchronized static void enterLoggerContext(Object context) {
        if (contextList.get() == null) {
            contextList.set(new Stack<>());
        }
        contextList.get().push(context);
    }

    public synchronized static void leaveLoggerContext() {
        contextList.get().pop();
        if (contextList.get().empty()) {
            contextList.set(null);
        }
    }

    public synchronized static List<Object> getLoggerContext() {
        return new LinkedList<>(contextList.get());
    }

    public static synchronized boolean objectInContext(Object context) {
        return contextList.get() != null && contextList.get().contains(context);
    }
}
