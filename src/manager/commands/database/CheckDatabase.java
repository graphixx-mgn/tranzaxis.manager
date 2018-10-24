package manager.commands.database;

import codex.command.EntityCommand;
import codex.model.EntityModel;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.NetTools;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import manager.nodes.Database;

public class CheckDatabase extends EntityCommand<Database> {
    
    private final static Pattern   SPLIT   = Pattern.compile("([\\d\\.]+|[^\\s]+):(\\d+)/");
    private final static ImageIcon WARN    = ImageUtils.resize(ImageUtils.getByPath("/images/unavailable.png"),  28, 28);
    private final static ImageIcon ACTIVE  = ImageUtils.resize(ImageUtils.getByPath("/images/lamp.png"),  28, 28);
    private final static ImageIcon PASSIVE = ImageUtils.resize(ImageUtils.getByPath("/images/event.png"), 28, 28);

    public CheckDatabase() {
        super("activity", null, PASSIVE, Language.get(Database.class.getSimpleName(), "command@activity"), null);
        getButton().setInactive(true);
        
        activator = (databases) -> {
            if (databases != null && databases.size() > 0 && !(databases.size() > 1 && !multiContextAllowed())) {
                String dbUrl = databases.get(0).getDatabaseUrl(true);
                if (dbUrl != null) {
                    if (checkUrlPort(dbUrl)) {
                        getButton().setIcon(ACTIVE);
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
    public void execute(Database database, Map<String, IComplexType> params) {
        // Do nothing
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
    
    @Override
    public Kind getKind() {
        return Kind.Info;
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        super.modelSaved(model, changes);
        if (changes.contains(Database.PROP_BASE_URL)) {
            activate();
        }
    }
    
    public static boolean checkUrlPort(String dbUrl) {
        Matcher verMatcher = SPLIT.matcher(dbUrl);
        if (verMatcher.find()) {
            String  host = verMatcher.group(1);
            Integer port = Integer.valueOf(verMatcher.group(2));
            try {
                return NetTools.isPortAvailable(host, port, 50);
            } catch (IllegalStateException e) {
                return false;
            }
        } else {
            return false;
        }
    }
    
}
