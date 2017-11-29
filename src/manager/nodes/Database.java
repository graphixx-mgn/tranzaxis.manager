package manager.nodes;

import codex.command.EntityCommand;
import codex.command.ValueProvider;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.database.RowSelector;
import codex.log.Level;
import codex.log.Logger;
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
import codex.utils.NetTools;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getValue("dbSchema");
        },
        "dbSchema", "dbPass");
        model.addUserProp("instanceId", new Int(null), true, Access.Select);
        model.addUserProp("layerURI", new Str(null), true, Access.Select);
        model.addUserProp("userNote", new Str(null), false, null);
        
        addCommand(new CheckDatabase());
        addCommand(new TestOracle());
        addCommand(new TestParams());
        
        model.getEditor("instanceId").addCommand(new ValueProvider(new RowSelector()));
    }
    
    private boolean checkPort(String dbUrl) {
        Matcher verMatcher = Pattern.compile("([\\d\\.]+):(\\d+)/").matcher(dbUrl);
        if (verMatcher.find()) {
            String  host = verMatcher.group(1);
            Integer port = Integer.valueOf(verMatcher.group(2));
            return NetTools.isPortAvailable(host, port, 35);
        } else {
            return false;
        }
    }
    
    public class TestOracle extends EntityCommand {

        public TestOracle() {
            super(
                    "test_oracle", "Test oracle connection",
                    ImageUtils.resize(ImageUtils.getByPath("/images/branch.png"), 28, 28), 
                    "Test oracle connection",
                    (entity) -> true
            );
        }

        @Override
        public void execute(Entity context, Map<String, IComplexType> params) {
            if (IComplexType.notNull(context.model.getValue("dbUrl"), context.model.getValue("dbSchema"), context.model.getValue("dbPass"))) {
                if (checkPort((String) context.model.getValue("dbUrl"))) {
                    IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
                    try {
                        Integer connectionID = DAS.registerConnection(
                                "jdbc:oracle:thin:@//"+context.model.getValue("dbUrl"), 
                                (String) context.model.getValue("dbSchema"), 
                                (String) context.model.getValue("dbPass")
                        );
                        Logger.getLogger().info("Database connection ID: "+connectionID);
                        if (connectionID != null) {
                            ResultSet rset = DAS.select(connectionID, "SELECT ID, TITLE FROM RDX_INSTANCE");
                            try {
                                while (rset.next()) {
                                    Logger.getLogger().info(rset.getInt(1)+"-"+rset.getString(2));
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            } finally {
                                rset.getStatement().close();
                                rset.close();
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                } else {
                    Logger.getLogger().warn("Database listener not available");
                }
            } else {
                Logger.getLogger().warn("Database parameters are not filled completely");
            }
        }
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
