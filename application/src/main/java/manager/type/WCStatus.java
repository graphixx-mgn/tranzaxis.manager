package manager.type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;

public enum WCStatus implements Iconified {

    Unknown(ImageUtils.getByPath("/images/progress.png")),

    Absent(ImageUtils.getByPath("/images/svn_none.png")),
    Invalid(ImageUtils.getByPath("/images/svn_invalid.png")),
    Interrupted(ImageUtils.getByPath("/images/svn_interrupted.png")),
    Erroneous(ImageUtils.getByPath("/images/svn_erroneous.png")),
    Successful(ImageUtils.getByPath("/images/svn_successful.png"));
    
    private final String    title;
    private final ImageIcon icon;
    
    private WCStatus(ImageIcon icon) {
        this.title = Language.get(name().toLowerCase());
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
