package manager.nodes;

import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
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
import java.util.function.Function;
import manager.commands.CheckDatabase;

public class Database extends Entity {
    
    public static IDatabaseAccessService DAS;
    static {
        ServiceRegistry.getInstance().registerService(OracleAccessService.getInstance());
        DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
    }
    
    private final Function<Boolean, Integer> connectionGetter = (showError) -> {
        if (IComplexType.notNull(
                model.getUnsavedValue("dbUrl"), 
                model.getUnsavedValue("dbSchema"), 
                model.getUnsavedValue("dbPass")
            )
        ) {
            String dbUrl = (String) model.getUnsavedValue("dbUrl");
            if (!CheckDatabase.checkUrlPort(dbUrl)) {
                if (showError) {
                    MessageBox.show(MessageType.WARNING, MessageFormat.format(
                            Language.get(Database.class.getSimpleName(), "error@unavailable"),
                            dbUrl.substring(0, dbUrl.indexOf("/"))
                    ));
                }
                return null;
            }
            try {
                return DAS.registerConnection(
                        "jdbc:oracle:thin:@//"+dbUrl, 
                        (String) model.getUnsavedValue("dbSchema"), 
                        (String) model.getUnsavedValue("dbPass")
                );
            } catch (SQLException e) {
                if (showError) {
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                }
            }
        } else {
            if (showError) {
                MessageBox.show(
                        MessageType.WARNING, 
                        Language.get(Database.class.getSimpleName(), "error@notready")
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
