package manager.nodes;

import codex.model.Entity;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import java.util.LinkedList;

@RepositoryBranch.Branch(remoteDir = "releases", localDir = "releases", hasArchive = true)
public class ReleaseList extends RepositoryBranch {

    public ReleaseList(EntityRef owner) {
        this(owner, "title");
    }

    public ReleaseList(EntityRef owner, String PID) {
        super(owner, ImageUtils.getByPath("/images/releases.png"), PID, null);
    }

    @Override
    public void loadBranch() {
        getChildrenPIDs().forEach((childPID) -> {
            Entity instance = Entity.newInstance(getChildClass(), getOwner().toRef(), childPID);
            insert(instance);
        });
    }

    @Override
    public void unloadBranch() {
        new LinkedList<>(childrenList()).forEach(this::delete);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Release.class;
    }

}
