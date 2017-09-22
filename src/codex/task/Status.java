package codex.task;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

public enum Status implements Iconified {
    
    PENDING("Pending",    ImageUtils.resize(ImageUtils.getByPath("/images/wait.png"), 17, 17)),
    STARTED("Started",    ImageUtils.resize(ImageUtils.getByPath("/images/start.png"), 17, 17)),
    FINISHED("Finished",  ImageUtils.resize(ImageUtils.getByPath("/images/success.png"), 17, 17)),
    FAILED("Failed",      ImageUtils.resize(ImageUtils.getByPath("/images/stop.png"), 17, 17)),
    CANCELLED("Cancelled", ImageUtils.resize(ImageUtils.getByPath("/images/cancel.png"), 17, 17));
    
    private final String    title;
    private final ImageIcon icon;
    private final String    desc;
    
    private Status(String title, ImageIcon icon) {
        this.title = title;
        this.icon  = icon;
        this.desc  = Language.get("TaskStatus", title.toLowerCase());
    }
    
    @Override
    public final ImageIcon getIcon() {
        return icon;
    }
    
    public final String getDescription() {
        return desc;
    }

    @Override
    public String toString() {
        return title;
    }
    
}
