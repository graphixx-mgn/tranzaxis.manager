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

    void sendMessage(Message message, Handler handler);

    boolean contextAllowed(Class<? extends IContext> contextClass);
}