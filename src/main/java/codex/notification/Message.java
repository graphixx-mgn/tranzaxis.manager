package codex.notification;

import java.awt.TrayIcon;
import java.util.Objects;
import java.util.UUID;

public final class Message {
    private UUID                 uuid;
    private String               text;
    private TrayIcon.MessageType type;

    public Message(String text) {
        this(TrayIcon.MessageType.INFO, text);
    }

    public Message(TrayIcon.MessageType type, String text) {
        this.type = type;
        this.text = text;
        this.uuid = UUID.randomUUID();
    }

    public final UUID getUuid() {
        return uuid;
    }

    public final String getText() {
        return text;
    }

    public final TrayIcon.MessageType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return uuid.equals(message.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
