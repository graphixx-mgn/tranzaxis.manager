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
        boolean exists = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class).isInstanceExists(
                Message.class, (String) message.model.getUnsavedValue(EntityModel.PID), null
        );
        if (!exists) {
            try {
                message.model.commit(false);
                new LinkedList<>(listeners).forEach(listener -> listener.messagePosted(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

//    boolean deleteMessage(Message message) {
//        if (message.getStatus() != Message.MessageStatus.Deleted) {
//            message.setStatus(Message.MessageStatus.Deleted);
//            //new LinkedList<>(listeners).forEach(listener -> listener.messageUpdated(message));
//            return false;
//        } else {
//            if (message.delete()) {
//                //new LinkedList<>(listeners).forEach(listener -> listener.messageDeleted(message));
//            }
//            return true;
//        }
//    }

    void addListener(IInboxListener listener) {
        listeners.add(listener);
    }

    interface IInboxListener {
        void messagePosted(Message message);
    }
}
