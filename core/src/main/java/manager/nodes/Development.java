package manager.nodes;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;

public class Development extends BranchCatalog {

    private static final String SUB_DIR = "/dev";

    public Development(EntityRef owner) {
        this(owner, "title");
    }

    public Development(EntityRef owner, String PID) {
        super(owner, ImageUtils.getByPath("/images/development.png"), PID, null);
    }
    
    @Override
    public Class<? extends Entity> getChildClass() {
        return Offshoot.class;
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
