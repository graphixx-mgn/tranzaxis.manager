package codex.notification;

import java.awt.TrayIcon;
import java.util.Objects;
import java.util.UUID;

public final class Message {
    private final UUID                 uuid;
    private final String               head;
    private final String               text;
    private final TrayIcon.MessageType type;

    public Message(String text) {
        this(null, text);
    }

    public Message(String head, String text) {
        this(TrayIcon.MessageType.INFO, head, text);
    }

    public Message(TrayIcon.MessageType type, String head, String text) {
        this.type = type;
        this.head = head;
        this.text = text;
        this.uuid = UUID.randomUUID();
    }

    public final UUID getUuid() {
        return uuid;
    }

    public final String getHead() {
        return head;
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
