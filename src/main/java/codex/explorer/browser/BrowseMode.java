package codex.explorer.browser;

import codex.explorer.tree.INode;

import java.awt.*;

public abstract class BrowseMode<T extends Container> {

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

}
