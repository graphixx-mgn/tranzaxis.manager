package codex.notification;

import codex.context.IContext;
import codex.service.IService;

/**
 * Интерфейс сервиса отображения уведомлений.
 */
public interface INotificationService extends IService {

    @Override
    default String getTitle() {
        return "Notification Service";
    }

    default void registerChannel(IMessageChannel channel) {}
    default void sendMessage(IMessageChannel channel, Message message) {}

    Accessor getAccessor();
    abstract class Accessor {
        abstract boolean contextAllowed(Class<? extends IContext> contextClass);
    }
}