package codex.notification;

import codex.config.IConfigStoreService;
import codex.model.EntityModel;
import codex.service.ServiceRegistry;
import java.util.LinkedList;
import java.util.List;

public class MessageInbox implements IMessageHandler {

    private final static MessageInbox INSTANCE = new MessageInbox();
    static MessageInbox getInstance() {
        return INSTANCE;
    }

    private final List<IInboxListener> listeners = new LinkedList<>();

    private MessageInbox() {}

    @Override
    public void postMessage(Message message) {
        new Thread(() -> {
            boolean exists = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).isInstanceExists(
                    Message.class, (String) message.model.getUnsavedValue(EntityModel.PID), null
            );
            if (!exists) {
                synchronized (this) {
                    try {
                        message.model.commit(false);
                        new LinkedList<>(listeners).forEach(listener -> listener.messagePosted(message));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    void addListener(IInboxListener listener) {
        listeners.add(listener);
    }

    interface IInboxListener {
        void messagePosted(Message message);
    }
}
