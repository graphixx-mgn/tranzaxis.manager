package codex.notification;

import codex.log.Logger;
import codex.service.AbstractService;
import codex.type.Bool;
import codex.type.Str;
import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.AWTEventListener;
import static java.awt.event.WindowEvent.WINDOW_OPENED;
import java.util.Optional;
import javax.swing.JFrame;

/**
 * Сервис отображения уведомлений в системном трее. Уведомления назначаются на 
 * иконку в трее.
 */
public class NotificationService extends AbstractService<NotifyServiceOptions> implements INotificationService {
    
    private TrayIcon trayIcon;
    private AWTEventListener WND_LISTENER = (event) -> {
        JFrame frame = (JFrame) event.getSource();
        if (event.getID() == WINDOW_OPENED && frame.getTitle() != null && frame.getIconImage() != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(this.WND_LISTENER);
            trayIcon = new TrayIcon(frame.getIconImage(), frame.getTitle());
            trayIcon.setImageAutoSize(true);
            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }
    };
    
    /**
     * Конструктор сервиса.
     * @param trayIcon Иконка системного трея.
     */
    public NotificationService() {
        if (SystemTray.isSupported()) {
            Toolkit.getDefaultToolkit().addAWTEventListener(WND_LISTENER, AWTEvent.WINDOW_EVENT_MASK);
        } else {
            Logger.getLogger().warn("NSS: Notification not supported by operating system");
        }
    }
    
    @Override
    public void registerSource(String source) {
        if (getConfig().getSources().put(new Str(source), new Bool(true)) != null) {
            Logger.getLogger().debug("NSS: Registered notification source: ''{0}''", source);
        }
    }

    @Override
    public void showMessage(String source, String title, String details, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            Optional<Str> knownSource = getConfig().getSources().keySet().stream().filter((key) -> {
                return key.getValue().equals(source);
            }).findFirst();

            if (!knownSource.isPresent()) {
                Logger.getLogger().warn("NSS: Unknown notification source: ''{0}''", source);
                return;
            }

            if (Boolean.TRUE.equals(getConfig().getSources().get(knownSource.get()).getValue())) {
                trayIcon.displayMessage(title, details, type);
            }
        }
    }
    
}
