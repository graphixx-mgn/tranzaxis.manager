package manager.commands.database;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.log.Logger;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.NetTools;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import manager.nodes.Database;

public class CheckDatabase extends EntityCommand<Database> {
    
    private final static Pattern   SPLIT   = Pattern.compile("([\\d\\.]+|[^\\s]+):(\\d+)/");
    private final static ImageIcon WARN    = ImageUtils.getByPath("/images/unavailable.png");
    private final static ImageIcon ACTIVE  = ImageUtils.getByPath("/images/lamp.png");
    private final static ImageIcon PASSIVE = ImageUtils.getByPath("/images/event.png");

    public CheckDatabase() {
        super("activity", null, PASSIVE, Language.get(Database.class, "command@activity"), null);
        activator = databases -> {
            if (databases != null && databases.size() > 0 && !(databases.size() > 1 && !multiContextAllowed())) {
                String dbUrl = databases.get(0).getDatabaseUrl(true);
                if (dbUrl != null) {
                    if (checkUrlPort(dbUrl)) {
                        return new CommandStatus(true, ACTIVE);
                    } else {
                        Logger.getLogger().warn(
                                Language.get(Database.class, "error@unavailable", Language.DEF_LOCALE),
                                databases.get(0).getPID(), dbUrl.substring(0, dbUrl.indexOf("/"))
                        );
                        return new CommandStatus(true, PASSIVE);
                    }
                } else {
                    return new CommandStatus(true, ImageUtils.combine(PASSIVE, WARN));
                }
            } else {
                return new CommandStatus(false, PASSIVE);
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
    
    public static boolean checkUrlPort(String dbUrl) {
        Matcher verMatcher = SPLIT.matcher(dbUrl);
        if (verMatcher.find()) {
            String host = verMatcher.group(1);
            int    port = Integer.valueOf(verMatcher.group(2));
            try {
                return NetTools.isPortAvailable(host, port, 250);
            } catch (IllegalStateException e) {
                return false;
            }
        } else {
            return false;
        }
    }
    
}
