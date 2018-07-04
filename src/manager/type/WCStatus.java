package manager.type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;


public enum WCStatus implements Iconified {

    Absent(Language.get("none"), ImageUtils.getByPath("/images/svn_none.png")),
    Interrupted(Language.get("interrupted"), ImageUtils.getByPath("/images/svn_interrupted.png")),
    Erroneous(Language.get("erroneous"), ImageUtils.getByPath("/images/svn_erroneous.png")),
    Succesfull(Language.get("successful"), ImageUtils.getByPath("/images/svn_successful.png"));
    
    private final String    title;
    private final ImageIcon icon;
    
    private WCStatus(String title, ImageIcon icon) {
        this.title  = title;
        this.icon   = icon;
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
