package codex.service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class ServiceCallContext {

    private static final ThreadLocal<Stack<IContext>> contextStack = new  ThreadLocal<>();

    public synchronized static void enterContext(IContext context) {
        if (contextStack.get() == null) {
            contextStack.set(new Stack<>());
        }
        contextStack.get().push(context);
    }

    public synchronized static void leaveContext() {
        contextStack.get().pop();
        if (contextStack.get().empty()) {
            contextStack.set(null);
        }
    }

    public synchronized static void clearContext() {
        contextStack.set(null);
    }

    public synchronized static List<IContext> getContext() {
        return contextStack.get() == null ? Collections.emptyList() : new LinkedList<>(contextStack.get());
    }

}
