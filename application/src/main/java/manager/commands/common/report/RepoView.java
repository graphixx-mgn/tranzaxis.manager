package manager.commands.common.report;

import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.model.Catalog;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import manager.commands.common.DiskUsageReport;
import manager.nodes.Repository;
import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

public class RepoView extends Catalog implements Comparable {

    private final static IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
    private final static ImageIcon IMG_REPO  = ImageUtils.getByPath("/images/repository.png");
    private final static ImageIcon IMG_TRASH = ImageUtils.combine(ImageUtils.grayscale(IMG_REPO), ImageUtils.getByPath("/images/unavailable.png"));

    public RepoView(EntityRef owner, String repoDirName) {
        super(
                owner,
                repoDirName.equals(DiskUsageReport.TRASH) ? IMG_TRASH : IMG_REPO,
                repoDirName,
                null
        );
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return Entry.class;
    }

    List<Entity> getLinkedEntities() {
        if (getOwner() != null) {
            return CAS.findReferencedEntries(Repository.class, getOwner().getID()).stream()
                    .filter(foreignLink -> !foreignLink.isIncoming)
                    .map(foreignLink -> EntityRef.build(foreignLink.entryClass, foreignLink.entryID).getValue())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public int compareTo(Object object) {
        RepoView view = (RepoView) object;
        if (getOwner() == null && view.getOwner() != null) {
            return -1;
        } else if (getOwner() != null && view.getOwner() == null) {
            return 1;
        } else {
            return Integer.compare(
                    getOwner().getID(),
                    view.getOwner().getID()
            );
        }
    }

    public void sortChildren() {
        List<INode> children = childrenList().stream()
                .sorted(Comparator.comparingInt(
                        node -> node.getClass().getAnnotation(BranchLink.class).priority()
                ))
                .collect(Collectors.toList());
        for (INode node : children) {
            int idx = children.indexOf(node);
            move(node, idx);
        }
    }

}