package codex.context;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.*;
import java.util.Objects;

public class ContextPresentation implements Iconified {

    private final String    title;
    private final ImageIcon icon;
    private final Class<? extends IContext> contextClass;

    public ContextPresentation(Class<? extends IContext> contextClass) {
        this.contextClass = contextClass;
        this.icon  = ImageUtils.getByPath(contextClass, contextClass.getAnnotation(IContext.Definition.class).icon());
        this.title = contextClass.getAnnotation(IContext.Definition.class).title();
    }

    @Override
    public String toString() {
        return title;
    }

    public Class<? extends IContext> getContextClass() {
        return contextClass;
    }

    @Override
    public ImageIcon getIcon() {
        return icon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextPresentation that = (ContextPresentation) o;
        return Objects.equals(contextClass, that.contextClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextClass);
    }
}
