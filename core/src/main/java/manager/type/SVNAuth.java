package manager.type;

import codex.type.Iconified;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.ImageIcon;


public enum SVNAuth implements Iconified {
    
    None(Language.get("none"), ImageUtils.getByPath("/images/auth_none.png")),
    Password(Language.get("pass"), ImageUtils.getByPath("/images/auth_pass.png"));
    //Certificate(Language.get("pass"), ImageUtils.getByPath("/images/auth_cert.png"));
    
    private final String    title;
    private final ImageIcon icon;
    
    private SVNAuth(String title, ImageIcon icon) {
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
