package codex.notification;

import codex.context.ContextView;
import codex.context.IContext;
import codex.log.Logger;
import codex.service.*;
import java.util.LinkedList;
import java.util.List;

/**
 * Сервис отображения уведомлений.
 */
@IService.Definition(optional = true)
@IContext.Definition(id = "NSS", name = "Notification Service", icon = "/images/notify.png")
public class NotificationService extends AbstractService<NotifyServiceOptions> implements INotificationService, IContext {

    private final List<IMessageChannel> channels = new LinkedList<>();

    @Override
    public void startService() {
        registerChannel(TrayInformer.getInstance());
        super.startService();
    }

    @Override
    public Accessor getAccessor() {
        return new Accessor() {
            @Override
            boolean contextAllowed(Class<? extends IContext> contextClass) {
                NotifyCondition condition = getConfig().getSources().get(new ContextView(contextClass));
                return condition != null && condition.getCondition().get();
            }
        };
    }

    @Override
    public void registerChannel(IMessageChannel channel) {
        if (!channels.contains(channel)) {
            channels.add(channel);
            Logger.getLogger().debug("Register channel ''{0}''", channel.getTitle());
        }
    }

    @Override
    public void sendMessage(IMessageChannel channel, Message message) {
        if (channels.contains(channel)) {
            channel.sendMessage(message);
        }
    }
    
}
