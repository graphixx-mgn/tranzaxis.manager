package manager.nodes;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.mask.DataSetMask;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import manager.commands.CheckDatabase;
import manager.commands.EditSAPPorts;

public class Database extends Entity {
    
    private static final EntityCommand CHECK = new CheckDatabase();
    private static final EntityCommand REMAP = new EditSAPPorts();
    
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
    
    private final Supplier<Integer> connectionSupplier = () -> {
        return connectionGetter.apply(true);
    };
    
    private final IDataSupplier<String> instanceSupplier = new RowSelector(
            RowSelector.Mode.Row, connectionSupplier, 
            "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
    );
    
    private final IDataSupplier<String> layerSupplier = new RowSelector(
            RowSelector.Mode.Row, connectionSupplier, 
            "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
    );
    
    private final IDataSupplier<String> versionSupplier = () -> {
        List<String> layerUri = (List<String>) model.getUnsavedValue("layerURI");
        return new RowSelector(
                RowSelector.Mode.Row, connectionSupplier, 
                "SELECT VERSION FROM RDX_DDSVERSION WHERE LAYERURI = ?",        
                layerUri == null ? null : layerUri.get(0)
        ).call();
    };

    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask(
                        "((([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))|[^\\s]+):"+
                        "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{1,3}|[0-9])"+
                        "/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp("dbSchema",   new Str(null), true, null);
        model.addUserProp("dbPass",     new Str(null), true, Access.Select);
        model.addUserProp("instanceId", new ArrStr().setMask(new DataSetMask(
                "{0} - {1}", instanceSupplier
        )), true, Access.Select);
        model.addUserProp("layerURI", new ArrStr().setMask(new DataSetMask(
                "{0}", layerSupplier
        )),  true, Access.Select);
        model.addUserProp("version", new ArrStr().setMask(new DataSetMask(
                "{0}", versionSupplier
        )), true, null);
        model.addUserProp("userNote", new Str(null), false, null);
        
        addCommand(CHECK);
        addCommand(REMAP);
    }
    
    public Integer getConnectionID() {
        return connectionGetter.apply(false);
    }
    
}
