package manager.nodes;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class ReleaseList extends BranchCatalog {

    private static final String SUB_DIR = "/releases";

    public ReleaseList(EntityRef owner) {
        this(owner, "title");
    }

    public ReleaseList(EntityRef owner, String PID) {
        super(owner, ImageUtils.getByPath("/images/releases.png"), PID, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Release.class;
    }

    @Override
    String getSubDirectory() {
        return SUB_DIR;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }
    
    Repository getRepository() {
        return (Repository) this.getOwner();
    }
    
}
