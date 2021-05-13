package codex.task;

import net.jcip.annotations.ThreadSafe;
import java.util.concurrent.ExecutionException;

/**
 * Исключение, которое прерывает исполнение задачи с выводом ошибки в GUI.
 */
@ThreadSafe
public class ExecuteException extends ExecutionException {
    
    private final String desc;

    /**
     * Конструктор исключения.
     * @param guiMessage Сообщение для вывода в виджет задачи.
     * @param logMessage Сообщение для вывода в лог.
     */
    public ExecuteException(String guiMessage, String logMessage) {
        super(guiMessage);
        this.desc = logMessage;
    }
    
    /**
     * Возвращает детально описание ошибки.
     */
    public String getDescription() {
        return desc;
    }
    
}
