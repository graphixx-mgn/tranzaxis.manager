package codex.explorer.browser;

import codex.explorer.tree.AbstractNode;
import codex.explorer.tree.INode;
import codex.utils.Language;
import java.awt.*;
import java.util.LinkedList;
import java.util.List;

public abstract class BrowseMode<T extends Container> {

    public static String SELECTOR_TITLE = Language.get(Browser.class, "selector@title");
    public static String EDITOR_TITLE   = Language.get(Browser.class, "editor@title");

    protected final T container;
    protected BrowseMode(Class<T> containerClass) {
        try {
            this.container = containerClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    final T getView() {
        return container;
    }

    abstract void browse(INode node);

    public static String getDescription(java.util.List<Class> classList, String key) {
        for (Class aClass : classList) {
            String desc = Language.get(aClass, key);
            if (!desc.equals(Language.NOT_FOUND)) {
                return desc;
            }
        }
        return null;
    }

    public static List<Class> getClassHierarchy(INode node) {
        List<Class> classList = new LinkedList<>();
        Class nextClass = node.getClass();
        do {
            classList.add(nextClass);
            nextClass = nextClass.getSuperclass();
        } while (nextClass != AbstractNode.class);
        return classList;
    }
}
