package manager.commands;

import codex.command.EntityCommand;
import codex.database.IDatabaseAccessService;
import codex.database.OracleAccessService;
import codex.log.Logger;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.service.ServiceRegistry;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.NetTools;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import manager.nodes.Database;

public class CheckDatabase extends EntityCommand {
    
    private final static Pattern   SPLIT   = Pattern.compile("([\\d\\.]+|[^\\s]+):(\\d+)/");
    private final static ImageIcon WARN    = ImageUtils.resize(ImageUtils.getByPath("/images/unavailable.png"),  28, 28);
    private final static ImageIcon ACTIVE  = ImageUtils.resize(ImageUtils.getByPath("/images/lamp.png"),  28, 28);
    private final static ImageIcon PASSIVE = ImageUtils.resize(ImageUtils.getByPath("/images/event.png"), 28, 28);

    public CheckDatabase() {
        super("activity", null, PASSIVE, Language.get(Database.class.getSimpleName(), "command@activity"), null);
        getButton().setInactive(true);
        
        activator = (entities) -> {
            if (entities != null && entities.length > 0 && !(entities.length > 1 && !multiContextAllowed())) {
                String dbUrl = (String) entities[0].model.getUnsavedValue("dbUrl");
                if (dbUrl != null && !entities[0].getInvalidProperties().contains("dbUrl")) {
                    if (checkUrlPort(dbUrl)) {
                        getButton().setIcon(ACTIVE);
                        startService(
                                entities[0],
                                "jdbc:oracle:thin:@//"+dbUrl, 
                                (String) entities[0].model.getUnsavedValue("dbSchema"), 
                                (String) entities[0].model.getUnsavedValue("dbPass")
                        );
                    } else {
                        getButton().setIcon(PASSIVE);
                    }
                } else {
                    getButton().setIcon(ImageUtils.combine(PASSIVE, WARN));
                }
                getButton().setEnabled(true);
            } else {
                getButton().setIcon(PASSIVE);
                getButton().setEnabled(false);
            }
        };
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> params) {
        // Do nothing
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        super.modelSaved(model, changes);
        if (changes.contains("dbUrl")) {
            activate();
        }
    }
    
    public static boolean checkUrlPort(String dbUrl) {
        Matcher verMatcher = SPLIT.matcher(dbUrl);
        if (verMatcher.find()) {
            String  host = verMatcher.group(1);
            Integer port = Integer.valueOf(verMatcher.group(2));
            return NetTools.isPortAvailable(host, port, 25);
        } else {
            return false;
        }
    }
    
    private void startService(Entity context,  String url, String user, String password) {
        Thread starter = new Thread(() -> {
            try {
                Integer ID = ((IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class)).registerConnection(
                        url, user, password
                );
            } catch (SQLException e) {
                Logger.getLogger().warn(
                        "Unable to connect to database ''{0}'': {1}",
                        context, e.getMessage()
                );
            }
        });
        starter.setDaemon(true);
        starter.start();
        starter = null;
    }
    
}
