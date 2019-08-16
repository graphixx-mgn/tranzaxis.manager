package codex.notification;

import codex.log.Logger;
import codex.service.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Сервис отображения уведомлений.
 */
@Definition(optional = true)
public class NotificationService extends AbstractService<NotifyServiceOptions> implements INotificationService {

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
            Map<ContextPresentation, NotifyCondition> getSources() {
                return getConfig().getSources();
            }
        };
    }

    @Override
    public void registerChannel(IMessageChannel channel) {
        if (!channels.contains(channel)) {
            channels.add(channel);
            Logger.getLogger().debug("NSS: Register channel ''{0}''", channel.getTitle());
        }
    }

    @Override
    public void registerSource(INotificationContext source) {
        Map<ContextPresentation, NotifyCondition> sources = getConfig().getSources();
        sources.putIfAbsent(
                source.getClassPresentation(),
                source.getDefaultCondition()
        );
        getConfig().setSources(sources);
        try {
            getConfig().model.commit(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger.getLogger().debug("NSS: Register source ''{0}''", source.getClassPresentation());
    }

    @Override
    public void sendMessage(IMessageChannel channel, Message message) {
        if (channels.contains(channel)) {
            channel.sendMessage(message);
        }
    }
    
}
