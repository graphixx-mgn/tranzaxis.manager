package codex.type;

import codex.log.Level;
import codex.task.Status;
import javax.swing.ImageIcon;

/**
 * Интерфейс-маркер перечислений которые имеют связанную со значением иконку.
 * @see Status
 * @see Level
 */
public interface Iconified {
    
    /**
     * Возвращает иконку значения перечисления.
     */
    public ImageIcon getIcon();
    
}
