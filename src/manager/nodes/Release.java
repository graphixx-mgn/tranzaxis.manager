package manager.nodes;

import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.io.File;
import java.util.StringJoiner;


public class Release extends BinarySource {

    public Release(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/release.png"), title);
        
        // Properties
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
    }

    @Override
    public Class getChildClass() {
        return null;
    }
    
    @Override
    public String getRemotePath() {
        return new StringJoiner("/")
            .add((String) this.model.getOwner().model.getValue("repoUrl"))
            .add("releases")
            .add(model.getPID()).toString();
    }
    
    @Override
    public final String getLocalPath() {
        IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
        String workDir = EAS.getRoot().model.getValue("workDir").toString();
        String repoUrl = (String) this.model.getOwner().model.getValue("repoUrl");
        
        StringJoiner wcPath = new StringJoiner(File.separator)
            .add(workDir)
            .add("releases")
            .add(Repository.urlToDirName(repoUrl))
            .add(model.getPID());
        return wcPath.toString();
    }
    
}
