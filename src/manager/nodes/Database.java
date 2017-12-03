package manager.nodes;

import codex.command.EntityCommand;
import codex.command.ValueProvider;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.log.Level;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import codex.type.Int;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;
import manager.commands.CheckDatabase;

public class Database extends Entity {
    
    static {
        ServiceRegistry.getInstance().registerService(OracleAccessService.getInstance());
    }

    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask(
                        "(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5])):\\d{1,5}/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp("dbSchema", new Str(null), true, null);
        model.addUserProp("dbPass", new Str(null), true, Access.Select);
        model.addUserProp("instanceId", new Int(null), true, Access.Select);
        model.addUserProp("layerURI", new Str(null), true, Access.Select);
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getValue("layerURI")+"~"+model.getValue("instanceId");
        },
        "instanceId", "layerURI");
        model.addUserProp("userNote", new Str(null), false, null);
        
        addCommand(new CheckDatabase());
        addCommand(new TestParams());
        
        Supplier<Integer> connectionSupplier = () -> {
            if (IComplexType.notNull(
                    model.getUnsavedValue("dbUrl"), 
                    model.getUnsavedValue("dbSchema"), 
                    model.getUnsavedValue("dbPass")
                ) && !getInvalidProperties().contains("dbUrl")
            ) {
                String dbUrl = (String) model.getUnsavedValue("dbUrl");
                if (!CheckDatabase.checkUrlPort(dbUrl)) {
                    MessageBox.show(MessageType.ERROR, "Unable to connect to port:");
                    return null;
                }
                try {
                    return ((IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class)).registerConnection(
                            "jdbc:oracle:thin:@//"+dbUrl, 
                            (String) model.getUnsavedValue("dbSchema"), 
                            (String) model.getUnsavedValue("dbPass")
                    );
                } catch (SQLException e) {
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                }
            }
            return null;
        };
        model.getEditor("instanceId").addCommand(new ValueProvider(new RowSelector(
                connectionSupplier,
                "SELECT ID, TITLE FROM RDX_INSTANCE ORDER BY ID"
        )));
        model.getEditor("layerURI").addCommand(new ValueProvider(new RowSelector(
                connectionSupplier,
                "SELECT LAYERURI, VERSION, UPGRADEDATE FROM RDX_DDSVERSION"
        )));
    }
    
    public class TestParams extends EntityCommand {

        public TestParams() {
            super(
                    "test_params", "Test command parameters",
                    ImageUtils.resize(ImageUtils.getByPath("/images/development.png"), 28, 28), 
                    "Test command parameters",
                    (entity) -> true
            );
            setParameters(
                    new PropertyHolder("PARAM_ENUM", new codex.type.Enum(Level.Debug), true),
                    new PropertyHolder("PARAM_STR", new Str(null), true),
                    new PropertyHolder("PARAM_REF", new EntityRef(Database.class), true)
            );
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            params.forEach((name, value) -> {
                java.lang.System.err.println(name+": "+value);
            });
        }
    }
    
}
