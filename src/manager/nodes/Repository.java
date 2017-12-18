package manager.nodes;

import codex.mask.FileMask;
import codex.mask.RegexMask;
import codex.mask.StrSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.type.ArrStr;
import codex.type.FilePath;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.util.Arrays;
import java.util.List;

public class Repository extends Entity {
    
    private static final String AUTH_PASS = "SVN password";
    private static final String AUTH_KEY  = "SSH key file";
    
    public Repository(String title) {
        super(ImageUtils.getByPath("/images/repository.png"), title, null);
        
        model.addUserProp("repoUrl", new Str(null).setMask(
                new RegexMask("svn\\+[\\w]+://[\\w\\./]+", "Invalid SVN url")
        ), true, null);
        model.addUserProp("svnUser", new Str(null), true, Access.Select);
        List<String> authTypes = Arrays.asList(new String[] {"", AUTH_PASS, AUTH_KEY});
        model.addUserProp("svnAuthType", new ArrStr(authTypes).setMask(
                new StrSetMask()
        ), true, Access.Select);
        model.addUserProp("svnPass", new Str(null), false, Access.Select);
        model.addUserProp("svnKeyFile", new FilePath(null).setMask(new FileMask()), false, Access.Select);
        
        String authType = ((List<String>) model.getValue("svnAuthType")).get(0);        
        model.getEditor("svnPass").setEditable(AUTH_PASS.equals(authType));
        model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(authType));
        
        model.addChangeListener((name, oldValue, newValue) -> {
            if (name.equals("svnAuthType")) {
                model.getEditor("svnPass").setEditable(AUTH_PASS.equals(newValue.toString()));
                model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(newValue.toString()));
            }
        });
    }
    
}
