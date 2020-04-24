package codex.scheduler.events;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.explorer.tree.INode;
import codex.model.*;
import codex.scheduler.Event;
import codex.type.EntityRef;
import codex.utils.Language;
import java.util.UUID;

@ClassCatalog.Domains({Event.class})
@EntityDefinition(title = "class@title", icon="/images/command.png")
public class Startup extends Event {

    private final static String UNIQUE_WARN = Language.get("unique@warn");

    public static <E extends Entity> E newInstance(Class<E> entityClass, EntityRef owner, String PID) {
        if (owner.getValue().childrenList().stream().anyMatch(iNode -> iNode instanceof Startup)) {
            MessageBox.show(MessageType.WARNING, UNIQUE_WARN);
            return null;
        } else {
            return Event.newInstance(entityClass, owner, PID);
        }
    }

    public Startup(EntityRef owner, String title) {
        super(owner, title);

        if (title == null) {
            //noinspection unchecked
            model.getProperty(EntityModel.PID).getOwnPropValue().setValue(UUID.randomUUID().toString());
        }
        setPropertyRestriction(EntityModel.PID, Access.Any);

        if (getTitle() != null) {
            executeJob();
        }
    }

    @Override
    public final void setParent(INode parent) {
        if (parent != null) {
            updateTitle();
        }
        super.setParent(parent);
    }

    private void updateTitle() {
        String title = Language.get(getClass().getAnnotation(EntityDefinition.class).title());
        setTitle(title);
    }
}
