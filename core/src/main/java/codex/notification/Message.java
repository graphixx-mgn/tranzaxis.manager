package codex.notification;

import codex.model.Access;
import codex.model.Entity;
import codex.model.PolyMorph;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.apache.commons.codec.digest.DigestUtils;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class Message extends PolyMorph {

    final static String PROP_SEVERITY = "severity";
    final static String PROP_CREATED  = "time";
    final static String PROP_SUBJECT  = "subject";
    final static String PROP_CONTENT  = "content";
    final static String PROP_STATUS   = "status";

    public static Builder getBuilder() {
        return getBuilder(DefaultMessage.class);
    }

    public static <M extends Message> Builder<M> getBuilder(Class<M> messageClass) {
        return getBuilder(messageClass, null);
    }

    public static <M extends Message> Builder<M> getBuilder(Class<M> messageClass, Supplier<String> supplierUID) {
        return Entity.newPrototype(messageClass).new Builder<M>(supplierUID);
    }

    private List<IMessageAction> actions = new LinkedList<>();

    public Message(EntityRef owner, String UID) {
        super(null, UID);

        // Properties
        model.addUserProp(PROP_SEVERITY, new Enum<>(Severity.None), true, Access.Any);
        model.addUserProp(PROP_CREATED,  new DateTime(), true, Access.Any);
        model.addUserProp(PROP_SUBJECT,  new Str(), true, Access.Any);
        model.addUserProp(PROP_CONTENT,  new Str(), true, Access.Any);
        model.addUserProp(PROP_STATUS,   new Enum<>(MessageStatus.New), true, Access.Any);

        registerColumnProperties(PROP_CREATED, PROP_STATUS, PROP_SEVERITY, PROP_SUBJECT, PROP_CONTENT);

        // Remove default change listener
        model.removeChangeListener(this);
    }

    final void setStatus(MessageStatus status) {
        model.setValue(PROP_STATUS, status);
        SwingUtilities.invokeLater(() -> {
            try {
                model.commit(false, PROP_STATUS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    final long getTime() {
        return getCreated().getTime();
    }

    final Date getCreated() {
        return (Date) model.getUnsavedValue(PROP_CREATED);
    }

    final MessageStatus getStatus() {
        return (MessageStatus) model.getUnsavedValue(PROP_STATUS);
    }

    protected final void setSeverity(Severity severity) {
        model.setValue(PROP_SEVERITY, severity);
    }

    final Severity getSeverity() {
        return (Severity) model.getUnsavedValue(PROP_SEVERITY);
    }

    protected final void setSubject(String subject) {
        model.setValue(PROP_SUBJECT, subject);
    }

    final String getSubject() {
        return (String) model.getUnsavedValue(PROP_SUBJECT);
    }

    protected final void setContent(String content) {
        model.setValue(PROP_CONTENT, content);
    }

    final String getContent() {
        return (String) model.getUnsavedValue(PROP_CONTENT);
    }

    protected final void addActions(IMessageAction... actions) {
        this.actions.addAll(Arrays.asList(actions));
    }

    final List<IMessageAction> getActions() {
        return new LinkedList<>(actions);
    }

    protected void onShow() {}
    protected void onDelete() {}
    protected void onStatusChange(MessageStatus prev, MessageStatus next) {}

    @Override
    protected void remove() {
        onDelete();
        super.remove();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return getPID().equals(message.getPID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPID());
    }

    public class Builder<M extends Message> {

        private final Supplier<String> UID;

        private Builder(Supplier<String> supplierUID) {
            this.UID = supplierUID == null ? () -> UUID.randomUUID().toString(): supplierUID;
            Message.this.setPID(DigestUtils.md5Hex(UID.get()));
            Message.this.model.setValue(PROP_CREATED, new Date());
        }

        public Builder<M> setSeverity(Severity severity) {
            Message.this.setSeverity(severity);
            return this;
        }

        public Builder<M> setSubject(String subject) {
            Message.this.setSubject(subject);
            return this;
        }

        public Builder<M> setContent(String content) {
            Message.this.setContent(content);
            return this;
        }

        @SuppressWarnings("unchecked")
        public M build() {
            if (getSeverity() == null) {
                setSeverity(Severity.None);
            }
            return (M) Message.this;
        }
    }


    enum MessageStatus {
        New(Color.decode("#3399FF")),
        Read(Color.GRAY);

        private final ImageIcon badge;

        MessageStatus(Color badgeColor) {
            badge = ImageUtils.createBadge(
                    Language.get(Message.class, "status@"+name().toLowerCase()),
                    blend(blend(badgeColor, Color.WHITE), Color.WHITE),
                    badgeColor,
                    1
            );
        }

        ImageIcon getBadge() {
            return badge;
        }

        private static Color blend(Color c0, Color c1) {
            double totalAlpha = c0.getAlpha() + c1.getAlpha();
            double weight0 = c0.getAlpha() / totalAlpha;
            double weight1 = c1.getAlpha() / totalAlpha;

            double r = weight0 * c0.getRed() + weight1 * c1.getRed();
            double g = weight0 * c0.getGreen() + weight1 * c1.getGreen();
            double b = weight0 * c0.getBlue() + weight1 * c1.getBlue();
            double a = Math.max(c0.getAlpha(), c1.getAlpha());

            return new Color((int) r, (int) g, (int) b, (int) a);
        }
    }


    public enum Severity implements Iconified {
        None(ImageUtils.getByPath("/images/event.png")),
        Feature(ImageUtils.getByPath("/images/plus.png")),
        Information(ImageUtils.getByPath("/images/info.png")),
        Warning(ImageUtils.getByPath("/images/warn.png")),
        Error(ImageUtils.getByPath("/images/stop.png"));

        private final ImageIcon icon;

        Severity(ImageIcon icon) {
            this.icon = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
    }


    public interface IMessageAction extends Iconified {
        String getTitle();
        void   doAction();
    }


    public static abstract class AbstractMessageAction implements IMessageAction {

        private final ImageIcon icon;
        private final String    text;

        protected AbstractMessageAction(ImageIcon icon, String text) {
            this.icon = icon;
            this.text = text;
        }

        @Override
        public final String getTitle() {
            return text;
        }

        @Override
        public final ImageIcon getIcon() {
            return icon;
        }
    }


    public static class DefaultMessage extends Message {

        public DefaultMessage(EntityRef owner, String UID) {
            super(owner, UID);
        }
    }
}
