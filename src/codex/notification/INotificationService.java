package codex.notification;

import codex.service.IService;
import java.awt.TrayIcon;

/**
 * Интерфейс сервиса отображения уведомлений в системном трее.
 */
public interface INotificationService extends IService {
    
    /**
     * Показать уведомление пользователю.
     * @param title Заголовок уведомления.
     * @param details Текст детального описания.
     * @param type Тип уведомления.
     */
    default void showMessage(String source, String title, String details, TrayIcon.MessageType type) {}
    
    @Override
    default String getTitle() {
        return "Notification Service";
    }
    
    default void registerSource(String source) {}
    
}