package codex.command;

import codex.log.Logger;
import codex.model.Entity;
import codex.type.IComplexType;
import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public abstract class EntityGroupCommand<V extends Entity> extends EntityCommand<V> {

    public EntityGroupCommand(String name, String title, ImageIcon icon, String hint, Predicate<V> available) {
        super(name, title, icon, hint, available);
    }

    @Override
    protected final void process() {
        List<V> context = getContext();
        if (!context.isEmpty()) {
            Logger.getLogger().debug("Perform command [{0}]. Group context: {1}", getName(), context);
            try {
                execute(context, getParameters());
            } catch (ParametersDialog.Canceled e) {
                // Do not call command
            }
        }
        activate();
    }

    @Override
    public final boolean multiContextAllowed() {
        return true;
    }

    @Override
    public final void execute(V context, Map<String, IComplexType> params) {
        throw new UnsupportedOperationException();
    }

    public abstract void execute(List<V> context, Map<String, IComplexType> params);
}
