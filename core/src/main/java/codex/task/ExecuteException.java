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
     * @param message Сообщение для вывода в виджет задачи.
     * @param desc Сообщение для вывода в лог.
     */
    public ExecuteException(String message, String desc) {
        super(message);
        this.desc = desc;
    }
    
    /**
     * Возвращает детально описание ошибки.
     */
    public String getDescription() {
        return desc;
    }
    
}
