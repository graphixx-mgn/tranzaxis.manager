package codex.notification;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.Window;
import java.util.function.Supplier;
import javax.swing.FocusManager;
import javax.swing.ImageIcon;

public enum NotifyCondition implements Iconified {
    
    ALWAYS(ImageUtils.getByPath("/images/lamp.png"), () -> true),
    INACTIVE(ImageUtils.getByPath("/images/event.png"), () -> {
        Window wnd = FocusManager.getCurrentManager().getActiveWindow();
        return wnd == null || !wnd.isActive();
    }),
    NEVER(ImageUtils.getByPath("/images/close.png"), () -> false);

    private final String    title;
    private final ImageIcon icon;
    private final Supplier<Boolean> condition;

    NotifyCondition(ImageIcon icon, Supplier<Boolean> condition) {
        this.title = Language.get(NotifyCondition.class, name().toLowerCase());
        this.icon  = icon;
        this.condition = condition;
    }

    @Override
    public ImageIcon getIcon() {
        return icon;
    }
    
    public Supplier<Boolean> getCondition() {
        return condition;
    }
    
    @Override
    public String toString() {
        return title;
    }
    
}
