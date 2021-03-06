package codex.task;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

/**
 * Перечисление статусов задачи {@link ITask}
 */
public enum Status implements Iconified {
    /**
     * Находится в стадии ожидания очереди исволнения.
     */
    PENDING("Pending",     ImageUtils.resize(ImageUtils.getByPath("/images/wait.png"), 17, 17)),
    /**
     * Запущена и исполняется.
     */
    STARTED("Started",     ImageUtils.resize(ImageUtils.getByPath("/images/start.png"), 17, 17)),
    /**
     * Успешно завершилась.
     */
    FINISHED("Finished",   ImageUtils.resize(ImageUtils.getByPath("/images/success.png"), 17, 17)),
    /**
     * Приостановлена.
     */
    PAUSED("Paused",       ImageUtils.resize(ImageUtils.getByPath("/images/pause.png"), 17, 17)),
    /**
     * Прервана вследствие ошибки при исполнении.
     */
    FAILED("Failed",       ImageUtils.resize(ImageUtils.getByPath("/images/stop.png"), 17, 17)),
    /**
     * Отменена по команде пользователя или вследствие ошибки в связанной задаче.
     */
    CANCELLED("Cancelled", ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 17, 17));
    
    private final String    title;
    private final ImageIcon icon;
    private final String    desc;
    
    Status(String title, ImageIcon icon) {
        this.title = title;
        this.icon  = icon;
        this.desc  = Language.get(title.toLowerCase());
    }
    
    @Override
    public final ImageIcon getIcon() {
        return icon;
    }
    
    /**
     * Возвращает текстовое описание состояния задачи.
     */
    public final String getDescription() {
        return desc;
    }

    /**
     * Признак что состояние финальное (задача завершилась)
     */
    public final boolean isFinal() {
        return this.equals(FINISHED) || this.equals(FAILED) || this.equals(CANCELLED);
    } 
    
    @Override
    public String toString() {
        return title;
    }
    
}
