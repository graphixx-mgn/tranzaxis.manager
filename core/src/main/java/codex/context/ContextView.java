package codex.context;

import codex.log.Level;
import codex.log.Logger;
import codex.log.LoggingSource;
import codex.model.Catalog;
import codex.model.Entity;
import codex.type.Bool;
import codex.type.Enum;
import codex.type.Iconified;
import java.util.Objects;

public class ContextView extends Catalog implements Iconified {

    public  final static String PROP_LEVEL = "level";
    private final Class<? extends IContext> contextClass;

    public ContextView(Class<? extends IContext> contextClass) {
        super(
                null,
                null,
                contextClass.getTypeName(),
                null
        );
        this.contextClass = contextClass;
        Logger.ContextInfo ctxInfo = Logger.getContextRegistry().getContext(contextClass);
        setTitle(ctxInfo.getName());
        setIcon(ctxInfo.getIcon());

        boolean isOption = contextClass.getAnnotation(LoggingSource.class).debugOption();
        Level   ctxLevel = ctxInfo.getLevel();
        model.addDynamicProp(
                PROP_LEVEL,
                isOption ? new Bool(ctxLevel == Level.Debug) : new Enum<>(ctxLevel),
                null, null
        );
    }

    public Class<? extends IContext> getContextClass() {
        return contextClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextView that = (ContextView) o;
        return Objects.equals(contextClass, that.contextClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextClass);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    @Override
    public boolean isOverridable() {
        return false;
    }
}
