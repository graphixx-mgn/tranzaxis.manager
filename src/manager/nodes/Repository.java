package manager.nodes;

import codex.editor.AbstractEditor;
import codex.explorer.tree.INode;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.lang.reflect.Field;
import manager.commands.LoadWC;
import manager.type.SVNAuth;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

public class Repository extends Entity {
    
    public Repository(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/repository.png"), title, null);
        
        MutablePropHolder svnUser = new MutablePropHolder("svnUser", new Str(null), true);
        MutablePropHolder svnPass = new MutablePropHolder("svnPass", new Str(null), true);

        // Properties
        model.addUserProp("repoUrl",  new Str(null).setMask(new RegexMask("svn(|\\+[\\w]+)://[\\w\\./\\d]+", "Invalid SVN url")), true, null);
        model.setPropUnique("repoUrl");
        model.addUserProp("authMode", new Enum(SVNAuth.None), true, Access.Select);
        model.addUserProp(svnUser, Access.Select);
        model.addUserProp(svnPass, Access.Select);
        model.addUserProp("locked", new Bool(false), false, Access.Any);
        
        // Property settings
        svnUser.setMandatory(model.getValue("authMode") == SVNAuth.Password);
        svnPass.setMandatory(model.getValue("authMode") == SVNAuth.Password);
        
        // Editor settings
        model.addPropertyGroup(Language.get("group@auth"), "authMode", "svnUser", "svnPass");
       
        model.getEditor("repoUrl").setEditable(!(Boolean) model.getValue("locked"));
        model.getEditor("svnUser").setVisible(model.getValue("authMode").equals(SVNAuth.Password));
        model.getEditor("svnPass").setVisible(model.getValue("authMode").equals(SVNAuth.Password));
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
            switch (name) {
                case "authMode":
                    model.getEditor("svnUser").setVisible(model.getUnsavedValue("authMode").equals(SVNAuth.Password));
                    model.getEditor("svnPass").setVisible(model.getUnsavedValue("authMode").equals(SVNAuth.Password));
                    svnUser.setMandatory(model.getUnsavedValue("authMode") == SVNAuth.Password);
                    svnPass.setMandatory(model.getUnsavedValue("authMode") == SVNAuth.Password);
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
    
    public ISVNAuthenticationManager getAuthManager() {
        SVNAuth authType = (SVNAuth) model.getUnsavedValue("authMode");
        switch (authType) {
            case Password:
                return new BasicAuthenticationManager(new SVNAuthentication[] {
                    new SVNPasswordAuthentication(
                            (String) model.getUnsavedValue("svnUser"), 
                            (String) model.getUnsavedValue("svnPass"), 
                            false
                    )
                });
            default:
                return new BasicAuthenticationManager(new SVNAuthentication[] {});
        }
    }
    
    public static String urlToDirName(String url) {
        return url.replaceAll("svn(|\\+[\\w]+)://([\\w\\./\\d]+)", "$2").replaceAll("[/\\\\]{1}", ".");
    }
    
    private class MutablePropHolder extends PropertyHolder {
    
        public MutablePropHolder(String name, IComplexType value, boolean require) {
            super(name, value, require);
        }
        
        public void setMandatory(boolean mandatory) {
            try {
                Field require = PropertyHolder.class.getDeclaredField("require");
                require.setAccessible(true);
                require.set(this, mandatory);
                ((AbstractEditor) model.getEditor(getName())).updateUI();
            } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    
    }
    
}
