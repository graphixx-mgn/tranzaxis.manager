package codex.notification;

import codex.context.ServiceCallContext;
import codex.log.Logger;
import codex.service.ServiceRegistry;
import codex.utils.ImageUtils;
import net.jcip.annotations.ThreadSafe;
import java.awt.*;

@ThreadSafe
public class TrayInformer implements IMessageHandler {

    private final static TrayInformer INSTANCE = new TrayInformer();
    static TrayInformer getInstance() {
        return INSTANCE;
    }

    private final TrayIcon trayIcon;

    @Override
    public void postMessage(Message message) {
        if (SystemTray.isSupported() && checkConditions()) {
            synchronized (trayIcon) {
                trayIcon.displayMessage(message.getSubject(), message.getContent(), getType(message));
            }
        }
    }

    private TrayInformer() {
        if (SystemTray.isSupported()) {
            Logger.getContextLogger(NotificationService.class).info("System notification is supported");
            Image trayImage = ImageUtils.getByPath("/images/project.png").getImage();
            trayIcon = new TrayIcon(trayImage);
            trayIcon.setImageAutoSize(true);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        } else {
            trayIcon = null;
            Logger.getContextLogger(NotificationService.class).warn("NSS: System notification not supported by operating system");
        }
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
}
