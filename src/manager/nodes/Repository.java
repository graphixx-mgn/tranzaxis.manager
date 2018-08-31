package manager.nodes;

import codex.explorer.tree.INode;
import codex.mask.FileMask;
import codex.mask.RegexMask;
import codex.mask.StrSetMask;
import codex.model.Access;
import codex.model.Entity;
import codex.type.ArrStr;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.FilePath;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.util.Arrays;
import java.util.List;
import manager.commands.LoadWC;

public class Repository extends Entity {
    
    private static final String       AUTH_PASS = "SVN password";
    private static final String       AUTH_KEY  = "SSH key file";
    private static final List<String> AUTH_TYPES = Arrays.asList(new String[] {"", AUTH_PASS, AUTH_KEY});
    
    public Repository(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/repository.png"), title, null);

        // Properties
        model.addUserProp("repoUrl", new Str(null).setMask(new RegexMask("svn(|\\+[\\w]+)://[\\w\\./\\d]+", "Invalid SVN url")), true, Access.Select);
        model.setPropUnique("repoUrl");
        model.addUserProp("svnUser", new Str(null), true, Access.Select);
        model.addUserProp("svnAuthType", new ArrStr(AUTH_TYPES).setMask(
                new StrSetMask()
        ), true, Access.Select);
        model.addUserProp("svnPass", new Str(null), false, Access.Select);
        model.addUserProp("svnKeyFile", new FilePath(null).setMask(new FileMask()), false, Access.Select);
        model.addUserProp("locked", new Bool(false), false, Access.Any);
        
        // Editor settings
        String authType = ((List<String>) model.getValue("svnAuthType")).get(0);
        model.getEditor("repoUrl").setEditable(!(Boolean) model.getValue("locked"));
        model.getEditor("svnPass").setEditable(AUTH_PASS.equals(authType));
        model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(authType));
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "svnAuthType":
                    model.getEditor("svnPass").setEditable(AUTH_PASS.equals(newValue.toString()));
                    model.getEditor("svnKeyFile").setEditable(AUTH_KEY.equals(newValue.toString()));
                    break;
            }
        });
        
        // Commands
        addCommand(new LoadWC());
        
        setMode(((Boolean) model.getValue("locked") ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        getCommand("load").execute(this, null);
    }
    
    public static String urlToDirName(String url) {
        return url.replaceAll("svn(|\\+[\\w]+)://([\\w\\./\\d]+)", "$2").replaceAll("[/\\\\]{1}", ".");
    }
    
}
