
package codex.task;

import net.jcip.annotations.ThreadSafe;

/**
 * Исключение, которое останавливает исполнение задачи.
 */
@ThreadSafe
public class CancelException extends Error {
    
}
