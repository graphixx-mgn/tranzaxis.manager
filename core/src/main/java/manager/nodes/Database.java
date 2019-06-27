package manager.nodes;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.function.Function;
import manager.commands.database.CheckDatabase;

public class Database extends Entity {
    
    private final static String PROP_BASE_URL  = "dbUrl";
    private final static String PROP_BASE_USER = "dbSchema";
    private final static String PROP_BASE_PASS = "dbPass";
    private final static String PROP_USER_NOTE = "userNote";
   
    private static final IDatabaseAccessService OAS = OracleAccessService.getInstance();
    
    static {
        ServiceRegistry.getInstance().registerService(OAS);
        CommandRegistry.getInstance().registerCommand(CheckDatabase.class);
    }
    
    private final Function<Boolean, Integer> connectionGetter = (showError) -> {
        String url  = getDatabaseUrl(true);
        String user = getDatabaseUser(true); 
        String pass = getDatabasePassword(true);

        if (IComplexType.notNull(url, user, pass)) {
            if (!CheckDatabase.checkUrlPort(url)) {
                if (showError) {
                    MessageBox.show(MessageType.WARNING, MessageFormat.format(
                            Language.get(Database.class, "error@unavailable"),
                            getPID(), url.substring(0, url.indexOf("/"))
                    ));
                }
                return null;
            }
            try {
                return Database.OAS.registerConnection("jdbc:oracle:thin:@//"+url, user, pass);
            } catch (SQLException e) {
                if (showError) {
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                } else {
                    Logger.getLogger().warn(
                            "Unable to open connection for database ''{0}'': {1}",
                            this, e.getMessage()
                    );
                }
            }
        } else {
            if (showError) {
                MessageBox.show(
                        MessageType.WARNING, 
                        Language.get(Database.class, "error@notready")
                );
            } else {
                Logger.getLogger().warn(
                        Language.get(Database.class, "error@notready", Locale.US)
                );
            }
        }
        return null;
    };
    
    public Database(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/database.png"), title, null);

        // Properties
        model.addUserProp(PROP_BASE_URL, 
                new Str(null).setMask(new RegexMask(
                        "((([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))|[^\\s]+):"+
                        "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{1,3}|[0-9])"+
                        "/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp(PROP_BASE_USER, new Str(null), true, null);
        model.addUserProp(PROP_BASE_PASS, new Str(null), true, Access.Select);
        model.addUserProp(PROP_USER_NOTE, new Str(null), false, null);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null) {
            CheckDatabase check = getCommand(CheckDatabase.class);
            check.setContext(this);
            check.activate();
        }
    }
    
    public final String getDatabaseUrl(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_URL) : model.getValue(PROP_BASE_URL));
    }
    
    public final String getDatabaseUser(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_USER) : model.getValue(PROP_BASE_USER));
    }
    
    public final String getDatabasePassword(boolean unsaved) {
        return (String) (unsaved? model.getUnsavedValue(PROP_BASE_PASS) : model.getValue(PROP_BASE_PASS));
    }
    
    public final String getUserNote() {
        return (String) model.getValue(PROP_USER_NOTE);
    }
    
    public final void setDatabaseUrl(String value) {
        model.setValue(PROP_BASE_URL, value);
    }
    
    public final void setDatabaseUser(String value) {
        model.setValue(PROP_BASE_USER, value);
    }
    
    public final void setDatabasePassword(String value) {
        model.setValue(PROP_BASE_PASS, value);
    }
    
    public final void setUserNote(String value) {
        model.setValue(PROP_USER_NOTE, value);
    }
    
    public Integer getConnectionID(boolean showError) {
        return connectionGetter.apply(showError);
    }
    
}
