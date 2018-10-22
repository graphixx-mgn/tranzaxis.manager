package manager.nodes;

import codex.explorer.tree.INode;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.LoadWC;
import manager.type.SVNAuth;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

public class Repository extends Entity {
    
    public final static String PROP_REPO_URL  = "repoUrl";
    public final static String PROP_AUTH_MODE = "authMode";
    public final static String PROP_SVN_USER  = "svnUser";
    public final static String PROP_SVN_PASS  = "svnPass";
    public final static String PROP_USER_NOTE = "userNote";
    public final static String PROP_LOCKED    = "locked";
    
    public Repository(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/repository.png"), title, null);
        
        PropertyHolder svnUser = new PropertyHolder(PROP_SVN_USER, new Str(null), true);
        PropertyHolder svnPass = new PropertyHolder(PROP_SVN_PASS, new Str(null), true);
        
        // Properties
        model.addUserProp(PROP_REPO_URL,  new Str(null).setMask(new RegexMask("svn(|\\+[\\w]+)://[\\w\\./\\d]+", "Invalid SVN url")), true, null);
        model.addUserProp(PROP_AUTH_MODE, new Enum(SVNAuth.None), true, Access.Select);
        model.addUserProp(svnUser, Access.Select);
        model.addUserProp(svnPass, Access.Select);
        model.addUserProp(PROP_USER_NOTE, new Str(null), false, null);
        model.addUserProp(PROP_LOCKED,    new Bool(false), false, Access.Any);
        
        // Property settings
        model.setPropUnique(PROP_REPO_URL);
        svnUser.setRequired(getAuthMode(true) == SVNAuth.Password);
        svnPass.setRequired(getAuthMode(true) == SVNAuth.Password);
        
        // Editor settings
        model.addPropertyGroup(Language.get("group@auth"), PROP_AUTH_MODE, PROP_SVN_USER, PROP_SVN_PASS);

        model.getEditor(PROP_SVN_USER).setVisible(getAuthMode(true) == SVNAuth.Password);
        model.getEditor(PROP_SVN_PASS).setVisible(getAuthMode(true) == SVNAuth.Password);
        
        // Handlers
        model.addChangeListener((name, oldValue, newValue) -> {
           switch (name) {
                case PROP_AUTH_MODE:
                    model.getEditor(PROP_SVN_USER).setVisible(getAuthMode(true) == SVNAuth.Password);
                    model.getEditor(PROP_SVN_PASS).setVisible(getAuthMode(true) == SVNAuth.Password);
                    svnUser.setRequired(getAuthMode(true) == SVNAuth.Password);
                    svnPass.setRequired(getAuthMode(true) == SVNAuth.Password);
                    break;
            }
        });
        
        // Commands
        addCommand(new LoadWC());
        
        setMode((isLocked(false) && getChildCount() > 0 ? INode.MODE_ENABLED : INode.MODE_NONE) + INode.MODE_SELECTABLE);
    }
    
    public final String getRepoUrl() {
        return (String) model.getValue(PROP_REPO_URL);
    }
    
    public final SVNAuth getAuthMode(boolean unsaved) {
        return (SVNAuth) (unsaved ? model.getUnsavedValue(PROP_AUTH_MODE) : model.getValue(PROP_AUTH_MODE));
    }
    
    public final String getSvnUser(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_SVN_USER) : model.getValue(PROP_SVN_USER));
    }
    
    public final String getSvnPass(boolean unsaved) {
        return (String) (unsaved ? model.getUnsavedValue(PROP_SVN_PASS) : model.getValue(PROP_SVN_PASS));
    }
    
    public final boolean isLocked(boolean unsaved) {
        return (unsaved ? model.getUnsavedValue(PROP_LOCKED) : model.getValue(PROP_LOCKED)) == Boolean.TRUE;
    }
    
    public final void setRepoUrl(SVNAuth value) {
        model.setValue(PROP_REPO_URL, value);
    }
    
    public final void setAuthMode(String value) {
        model.setValue(PROP_AUTH_MODE, value);
    }
    
    public final void setSvnUser(String value) {
        model.setValue(PROP_SVN_USER, value);
    }
    
    public final void setSvnPass(String value) {
        model.setValue(PROP_SVN_PASS, value);
    }
    
    public final void setLocked(boolean value) {
        model.setValue(PROP_LOCKED, value);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        getCommand("load").execute(this, null);
    }
    
    public ISVNAuthenticationManager getAuthManager() {
        SVNAuth authType = getAuthMode(true);
        switch (authType) {
            case Password:
                return new BasicAuthenticationManager(new SVNAuthentication[]{
                    new SVNPasswordAuthentication(
                            getSvnUser(true), 
                            getSvnPass(true), 
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
    
}
