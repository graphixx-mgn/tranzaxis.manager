package codex.notification;

import codex.context.ServiceCallContext;
import codex.log.Logger;
import codex.model.EntityModel;
import codex.model.IModelListener;
import codex.service.ServiceRegistry;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

@ThreadSafe
public class TrayInformer implements IMessageHandler {

    private final static TrayInformer INSTANCE = new TrayInformer();
    static TrayInformer getInstance() {
        return INSTANCE;
    }

    private final INotifier notifier;
    private final Supplier<NotifyServiceOptions> serviceOptions = () ->
            ServiceRegistry.getInstance().lookupService(INotificationService.class)
                .getAccessor()
                .getSettings();
    private final Supplier<Boolean> isEnabled = () -> serviceOptions.get()
            .model.getValue(NotifyServiceOptions.PROP_SYS_NOTIFY) == Boolean.TRUE;

    @Override
    public void postMessage(Message message) {
        if (isEnabled.get() && checkConditions()) {
            notifier.displayMessage(message.getSubject(), message.getContent(), getType(message));
        }
    }

    private TrayInformer() {
        notifier = SystemTray.isSupported() ? new TrayNotifier() : new INotifier() {};
        serviceOptions.get().model.addModelListener(new IModelListener() {
            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                if (changes.contains(NotifyServiceOptions.PROP_SYS_NOTIFY)) {
                    notifier.setEnabled(isEnabled.get());
                }
            }
        });
    }

    private TrayIcon.MessageType getType(Message message) {
        switch (message.getSeverity()) {
            case Error:       return TrayIcon.MessageType.ERROR;
            case Warning:     return TrayIcon.MessageType.WARNING;
            case Information:
            default:          return TrayIcon.MessageType.INFO;
        }
    }

    private boolean checkConditions() {
        INotificationService NSS = ServiceRegistry.getInstance().lookupService(INotificationService.class);
        return ServiceCallContext.getContextStack().stream()
                .anyMatch(NSS::contextAllowed);
    }

    private interface INotifier {
        default void setEnabled(boolean enabled) {}
        default void displayMessage(String caption, String text, TrayIcon.MessageType messageType) {}
    }

    private class TrayNotifier implements INotifier {

        private final TrayIcon trayIcon;

        TrayNotifier() {
            Logger.getContextLogger(NotificationService.class).debug("NSS: System notification is supported");
            trayIcon = new TrayIcon(ImageUtils.getByPath("/images/project.png").getImage());
            trayIcon.setImageAutoSize(true);
            setEnabled(isEnabled.get());
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled) {
                try {
                    SystemTray.getSystemTray().add(trayIcon);
                } catch (AWTException e) {
                    Logger.getContextLogger(NotificationService.class).warn("NSS: Unable to register system tray object", e);
                }
            } else {
                SystemTray.getSystemTray().remove(trayIcon);
            }
        }

        @Override
        public void displayMessage(String caption, String text, TrayIcon.MessageType messageType) {
            trayIcon.displayMessage(caption, text, messageType);
        }
    }
}
