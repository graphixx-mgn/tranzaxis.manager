package manager.commands.common.report;

import codex.type.EntityRef;
import codex.utils.ImageUtils;
import manager.nodes.ReleaseList;

@BranchLink(branchCatalogClass = ReleaseList.class, priority = 5)
public class ReleaseEntry extends DirEntry {

    public ReleaseEntry(EntityRef owner, String filePath) {
        super(owner, ImageUtils.getByPath("/images/release.png"), filePath);
    }

}
