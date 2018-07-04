package manager.type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;


public enum WCStatus implements Iconified {

    Absent("<none>", ImageUtils.getByPath("/images/svn_none.png")),
    Interrupted("<interrupted>", ImageUtils.getByPath("/images/svn_interrupted.png")),
    Erroneous("<erroneous>", ImageUtils.getByPath("/images/svn_erroneous.png")),
    Succesfull("<successful>", ImageUtils.getByPath("/images/svn_successful.png"));
    
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
