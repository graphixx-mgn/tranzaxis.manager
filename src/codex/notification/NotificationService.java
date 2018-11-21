package codex.notification;

import codex.service.AbstractService;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис отображения уведомлений в системном трее. Уведомления назначаются на 
 * иконку в трее.
 */
public class NotificationService extends AbstractService<NotifyServiceOptions> implements INotificationService {
    
    private final TrayIcon trayIcon;
    
    /**
     * Конструктор сервиса.
     * @param trayIcon Иконка системного трея.
     */
    public NotificationService(TrayIcon trayIcon) {
        if (trayIcon == null) {
            throw new IllegalStateException("Parameter 'trayIcon' can not be NULL");
        }
        this.trayIcon = trayIcon;
    }
    
    /**
     * Конструктор сервиса.
     * @param trayIconImage Изображение иконки системного трея, которая будет 
     * создана.
     * @param appName Подсказака к иконке, показывается при наведении мыши.
     */
    public NotificationService(Image trayIconImage, String appName) {
       this.trayIcon = new TrayIcon(trayIconImage, appName);
    }

    @Override
    public void showMessage(String title, String details, TrayIcon.MessageType type) {
        if (SystemTray.isSupported() && getConfig().getCondition().getCondition().get()) {
            AtomicBoolean iconExists = new AtomicBoolean(false);
            Arrays.asList(SystemTray.getSystemTray().getTrayIcons()).forEach((icon) -> {
                if (icon == trayIcon) {
                    iconExists.set(true);
                    icon.displayMessage(title, details, type);
                }
            });
            if (!iconExists.get()) {
                try {
                    SystemTray.getSystemTray().add(trayIcon);
                    trayIcon.displayMessage(title, details, type);
                } catch (AWTException e) {}
            }
        }
    }
    
}
