package codex.notification;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.*;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.apache.commons.codec.digest.DigestUtils;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class Message extends Catalog {

    final static String PROP_SEVERITY = "severity";
    final static String PROP_CREATED  = "time";
    final static String PROP_SUBJECT  = "subject";
    final static String PROP_CONTENT  = "content";
    final static String PROP_STATUS   = "status";
    final static String PROP_ACTION   = "action";

    public static Builder getBuilder() {
        return Entity.newPrototype(Message.class).new Builder();
    }

    public static Builder getBuilder(Supplier<String> supplierUID) {
        return Entity.newPrototype(Message.class).new Builder(supplierUID);
    }

    private IMessageAction action;

    private Message(EntityRef owner, String UID) {
        super(null, null, UID, null);

        // Properties
        model.addUserProp(PROP_SEVERITY, new Enum<>(Severity.None), true, null);
        model.addUserProp(PROP_CREATED,  new DateTime(), true, null);
        model.addUserProp(PROP_SUBJECT,  new Str(), true, null);
        model.addUserProp(PROP_CONTENT,  new Str(), true, null);
        model.addUserProp(PROP_STATUS,   new Enum<>(MessageStatus.New), true, null);
        model.addUserProp(PROP_ACTION,   new Str(), false, null);

//        action = getAction();

        // Remove default change listener
        model.removeChangeListener(this);
    }

    final MessageStatus getStatus() {
        return (MessageStatus) model.getUnsavedValue(PROP_STATUS);
    }

    final void setStatus(MessageStatus status) {
        model.setValue(PROP_STATUS, status);
        try {
            model.commit(false, PROP_STATUS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    final Severity getSeverity() {
        return (Severity) model.getUnsavedValue(PROP_SEVERITY);
    }

    final Date getCreated() {
        return (Date) model.getUnsavedValue(PROP_CREATED);
    }

    final String getSubject() {
        return (String) model.getUnsavedValue(PROP_SUBJECT);
    }

    String getContent() {
        return (String) model.getUnsavedValue(PROP_CONTENT);
    }

//    private void setAction(IMessageAction action) {
//        this.action = action;
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//            ObjectOutputStream objectOutputStream = new ObjectOutputStream(baos);
//            objectOutputStream.writeObject(action);
//            objectOutputStream.close();
//
//            model.setValue(PROP_ACTION, new String(Base64.getEncoder().encode(baos.toByteArray())));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public IMessageAction getAction() {
//        if (action != null) {
//            return action;
//        } else {
//            String binaryData = (String) model.getValue(PROP_ACTION);
//            if (binaryData != null) {
//                try {
//                    ObjectInputStream objectInputStream = new ObjectInputStream(
//                            new ByteArrayInputStream(Base64.getDecoder().decode(binaryData.getBytes()))
//                    );
//                    return (IMessageAction) objectInputStream.readObject();
//                } catch (IOException | ClassNotFoundException e) {
//                    return null;
//                }
//
//            }
//        }
//        return null;
//    }

    boolean delete() {
        return model.remove();
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

    public class Builder {

        private final Supplier<String> UID;

        private Builder() {
            this(null);
        }

        private Builder(Supplier<String> supplierUID) {
            this.UID = supplierUID == null ? () -> UUID.randomUUID().toString(): supplierUID;
            Message.this.setPID(DigestUtils.md5Hex(UID.get()));
            set(PROP_CREATED, new Date());
        }

        private Builder set(String propName, Object value) {
            Message.this.model.setValue(propName, value);
            return this;
        }

        public Builder setSeverity(Severity severity) {
            set(PROP_SEVERITY, severity);
            return this;
        }

        public Builder setSubject(String subject) {
            return set(PROP_SUBJECT, subject);
        }

        public Builder setContent(String subject) {
            return set(PROP_CONTENT, subject);
        }

//        public Builder setAction(IMessageAction action) {
//            Message.this.setAction(action);
//            return this;
//        }

        public Message build() {
            if (getSeverity() == null) {
                setSeverity(Severity.None);
            }
            return Message.this;
        }
    }


    enum MessageStatus {
        New(Color.decode("#3399FF")),
        Read(Color.GRAY),
        Deleted(Color.decode("#DE5347"));

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
        None(null),
        Information(ImageUtils.getByPath("/images/info.png")),
        Warning(ImageUtils.getByPath("/images/warn.png")),
        Error(ImageUtils.getByPath("/images/stop.png"))
        ;

        private final ImageIcon icon;

        Severity(ImageIcon icon) {
            this.icon = icon;
        }

        @Override
        public ImageIcon getIcon() {
            return icon;
        }
    }


    public interface IMessageAction extends Iconified, Serializable {
        String getTitle();
        void doAction();
    }
}
