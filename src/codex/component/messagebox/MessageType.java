package codex.component.messagebox;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

public enum MessageType implements Iconified {
    
    INFORMATION(ImageUtils.getByPath("/images/info.png")),
    CONFIRMATION(ImageUtils.getByPath("/images/confirm.png")),
    WARNING(ImageUtils.getByPath("/images/warn.png")),
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
