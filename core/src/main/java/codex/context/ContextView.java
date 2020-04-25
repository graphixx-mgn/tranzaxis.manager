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

    private final Logger.ContextInfo contextInfo;

    public ContextView(Logger.ContextInfo contextInfo) {
        super(
                null,
                contextInfo.getIcon(),
                contextInfo.getName(),
                null
        );
        this.contextInfo = contextInfo;

        boolean isOption = contextInfo.getClazz().getAnnotation(LoggingSource.class).debugOption();
        Level   ctxLevel = contextInfo.getLevel();
        model.addDynamicProp(
                PROP_LEVEL,
                isOption ? new Bool(ctxLevel == Level.Debug) : new Enum<>(ctxLevel),
                null, null
        );
    }

    public Class<? extends IContext> getContextClass() {
        return contextInfo.getClazz();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextView that = (ContextView) o;
        return Objects.equals(contextInfo, that.contextInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextInfo);
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
