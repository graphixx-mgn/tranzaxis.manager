package manager.nodes;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.log.Logger;
import codex.mask.RegexMask;
import codex.model.Access;
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
import manager.commands.CheckDatabase;

public class Database extends Entity {
   
    private static IDatabaseAccessService DAS = OracleAccessService.getInstance();
    
    static {
        ServiceRegistry.getInstance().registerService(DAS);
    }
    
    private final Function<Boolean, Integer> connectionGetter = (showError) -> {
        String url  = (String) model.getUnsavedValue("dbUrl");
        String user = (String) model.getUnsavedValue("dbSchema"); 
        String pass = (String) model.getUnsavedValue("dbPass");
        
        if (IComplexType.notNull(url, user, pass)) {
            if (!CheckDatabase.checkUrlPort(url)) {
                if (showError) {
                    MessageBox.show(MessageType.WARNING, MessageFormat.format(
                            Language.get(Database.class.getSimpleName(), "error@unavailable"),
                            model.getPID(), url.substring(0, url.indexOf("/"))
                    ));
                } else {
                    Logger.getLogger().warn(
                            Language.get(Database.class.getSimpleName(), "error@unavailable", Locale.US),
                            model.getPID(), url.substring(0, url.indexOf("/"))
                    );
                }
                return null;
            }
            try {
                return Database.DAS.registerConnection("jdbc:oracle:thin:@//"+url, user, pass);
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
                        Language.get(Database.class.getSimpleName(), "error@notready")
                );
            } else {
                Logger.getLogger().warn(
                        Language.get(Database.class.getSimpleName(), "error@notready", Locale.US)
                );
            }
        }
        return null;
    };
    
    public Database(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/database.png"), title, null);

        // Properties
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask(
                        "((([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))|[^\\s]+):"+
                        "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{1,3}|[0-9])"+
                        "/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp("dbSchema", new Str(null), true, null);
        model.addUserProp("dbPass",   new Str(null), true, Access.Select);
        model.addUserProp("userNote", new Str(null), false, null);
        
        // Commands
        addCommand(new CheckDatabase());
    }
    
    public Integer getConnectionID(boolean showError) {
        return connectionGetter.apply(showError);
    }
    
}
