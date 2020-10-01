package codex.notification;

import codex.context.IContext;
import codex.service.*;
import net.jcip.annotations.ThreadSafe;

/**
 * Сервис отображения уведомлений.
 */
@ThreadSafe
@IService.Definition()
@IContext.Definition(id = "NSS", name = "Notification Service", icon = "/images/notify.png")
public class NotificationService extends AbstractService<NotifyServiceOptions> implements INotificationService, IContext {

    @Override
    public void sendMessage(Message message, Handler handler) {
        handler.getHandler().postMessage(message);
    }

    @Override
    public boolean contextAllowed(Class<? extends IContext> contextClass) {
        return getSettings().getSources().entrySet().stream()
                .filter(ctxEntry -> ctxEntry.getKey().getContextClass().equals(contextClass))
                .anyMatch(ctxEntry -> ctxEntry.getValue().getCondition().get());
    }
}
