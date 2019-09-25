package codex.unit;

import codex.notification.Message;
import java.awt.*;
import java.util.*;
import java.util.List;

public class EventQueue {

    private Queue<Message>       queue = new PriorityQueue<>(Comparator.comparingInt(msg -> msg.getType().ordinal()));
    private List<IEventListener> listeners = new LinkedList<>();

    public synchronized void putMessage(Message msg) {
        if (!queue.contains(msg)) {
            queue.add(msg);
            Message head = queue.peek();
            new LinkedList<>(listeners).forEach(listener -> listener.putMessage(
                    msg, queue.size(), head == null ? TrayIcon.MessageType.NONE : head.getType()
            ));
        }
    }

    public synchronized void dropMessage(Message msg) {
        if (queue.contains(msg)) {
            Message found = findMessage(msg.getUuid());
            queue.remove(found);
            Message head = queue.peek();
            new LinkedList<>(listeners).forEach(listener -> listener.dropMessage(
                    found, queue.size(), head == null ? TrayIcon.MessageType.NONE : head.getType()
            ));
        }
    }

    public synchronized void addListener(IEventListener listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(IEventListener listener) {
        listeners.remove(listener);
    }

    private Message findMessage(UUID uuid) {
        return queue.stream()
                .filter(message -> message.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
    }

    public interface IEventListener {
        void putMessage(Message message, int queueSize, TrayIcon.MessageType maxSeverity);
        void dropMessage(Message message, int queueSize, TrayIcon.MessageType maxSeverity);
    }
}
