package manager.nodes;

import codex.command.ValueProvider;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.function.Function;
import java.util.function.Supplier;
import manager.commands.CheckDatabase;

public class Database extends Entity {
    
    public static IDatabaseAccessService DAS;
    static {
        ServiceRegistry.getInstance().registerService(OracleAccessService.getInstance());
        DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
    }
    
    private Function<Boolean, Integer> connectionGetter = (showError) -> {
        if (IComplexType.notNull(
                model.getUnsavedValue("dbUrl"), 
                model.getUnsavedValue("dbSchema"), 
                model.getUnsavedValue("dbPass")
            )
        ) {
            String dbUrl = (String) model.getUnsavedValue("dbUrl");
            if (!CheckDatabase.checkUrlPort(dbUrl)) {
                if (showError) {
                    MessageBox.show(MessageType.ERROR, MessageFormat.format(
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
                e.printStackTrace();
                if (showError) {
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                }
            }
        }
        return null;
    };
    
    private Supplier<Integer> connectionSupplier = () -> {
        return connectionGetter.apply(true);
    };

    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask(
                        "((([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))|[^\\s]+):\\d{1,5}/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp("dbSchema", new Str(null), true, null);
        model.addUserProp("dbPass", new Str(null), true, Access.Select);
        model.addUserProp("instanceId", new Int(null), true, Access.Select);
        model.addUserProp("layerURI", new Str(null), true, Access.Select);
        model.addDynamicProp("version", new Str(null), null, () -> {
            Integer connId = getConnectionID();
            if (connId != null && model.getValue("layerURI") != null) {
                try {
                    ResultSet rset = DAS.select(
                            connId, 
                            "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?",
                            new PropertyHolder("layer", new Str((String) model.getValue("layerURI")), false)
                    );
                    if (rset.next()) {
                        return rset.getString(1);
                    }
                } catch (SQLException e) {}
            }
            return null;
        },
        "layerURI");
        model.addUserProp("userNote", new Str(null), false, null);
        
        addCommand(new CheckDatabase());
//        addCommand(new EditSAPPorts());
        
        model.getEditor("instanceId").addCommand(new ValueProvider(new RowSelector(
                connectionSupplier,
                "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
        )));
        model.getEditor("layerURI").addCommand(new ValueProvider(new RowSelector(
                connectionSupplier,
                "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
        )));
    }
    
    public Integer getConnectionID() {
        return connectionGetter.apply(false);
    }
    
}
