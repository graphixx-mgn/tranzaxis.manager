package codex.notification;

import codex.service.ContextPresentation;
import codex.service.IService;
import java.util.Map;

/**
 * Интерфейс сервиса отображения уведомлений.
 */
public interface INotificationService extends IService {

    @Override
    default String getTitle() {
        return "Notification Service";
    }

    default void registerChannel(IMessageChannel channel) {}
    default void registerSource(INotificationContext source) {}
    default void sendMessage(IMessageChannel channel, Message message) {}

    Accessor getAccessor();
    abstract class Accessor {
        abstract Map<ContextPresentation, NotifyCondition> getSources();
    }
}