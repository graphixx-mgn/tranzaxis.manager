package codex.component.messagebox;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

/**
 * Тип окна сообщения.
 */
public enum MessageType implements Iconified {
    /**
     * Информационное сообщение.
     */
    INFORMATION(ImageUtils.getByPath("/images/info.png")),
    /**
     * Запрос на подтверждение. 
     */
    CONFIRMATION(ImageUtils.getByPath("/images/confirm.png")),
    /**
     * Предупреждение.
     */
    WARNING(ImageUtils.getByPath("/images/warn.png")),
    /**
     * Сообщение об ошибке.
     */
    ERROR(ImageUtils.getByPath("/images/stop.png"));
    
    private final String    title;
    private final ImageIcon icon;
    
    private MessageType(ImageIcon icon) {
        this.title = Language.get("message@"+this.name().toLowerCase());
        this.icon  = icon;
    }

    @Override
    public ImageIcon getIcon() {
        return icon;
    }
    
    @Override
    public String toString() {
        return title;
    }
    
}
