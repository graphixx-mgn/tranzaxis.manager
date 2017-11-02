package manager.commands;

import codex.command.EntityCommand;
import codex.model.Entity;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.NetTools;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import manager.nodes.Database;

public class CheckDatabase extends EntityCommand {
    
    private final static Pattern   SPLIT   = Pattern.compile("([\\d\\.]+):(\\d+)/");
    private final static ImageIcon ACTIVE  = ImageUtils.resize(ImageUtils.getByPath("/images/lamp.png"),  28, 28);
    private final static ImageIcon PASSIVE = ImageUtils.resize(ImageUtils.getByPath("/images/event.png"), 28, 28);

    public CheckDatabase() {
        super("activity", null, PASSIVE, Language.get(Database.class.getSimpleName(), "command@activity"), null);
        getButton().setFocusable(false);
        
        activator = (entities) -> {
            String dbUrl = (String) entities[0].model.getValue("dbUrl");
            getButton().setEnabled(
                    entities != null && entities.length > 0 && (
                            available == null || Arrays.asList(entities).stream().allMatch(available)
                    ) && dbUrl != null
            );
            if (getButton().isEnabled()) {
                getButton().setIcon(checkPort(dbUrl) ? ACTIVE : PASSIVE);
            }
        };
    }

    @Override
    public void execute(Entity entity) {
        // Do nothing
    }

    @Override
    public boolean disableWithContext() {
        return false;
    }
    
    @Override
    public void modelSaved(List<String> changes) {
        super.modelSaved(changes);
        if (changes.contains("dbUrl")) {
            activate();
        }
    }
    
    private boolean checkPort(String dbUrl) {
        Matcher verMatcher = SPLIT.matcher(dbUrl);
        if (verMatcher.find()) {
            String  host = verMatcher.group(1);
            Integer port = Integer.valueOf(verMatcher.group(2));
            return NetTools.isPortAvailable(host, port, 25);
        } else {
            return false;
        }
    }
    
}
