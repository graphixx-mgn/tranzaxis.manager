package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.EntityRef;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BranchCatalog extends Catalog {

    public BranchCatalog(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    @Override
    public abstract Class<? extends Entity> getChildClass();

    abstract String getSubDirectory();

    private List<SVNDirEntry> cachedBranchItems = null;
    public synchronized List<SVNDirEntry> getBranchItems() throws SVNException {
        if (cachedBranchItems == null) {
            List<SVNDirEntry> dirItems = SVN.list(getRepository().getRepoUrl() + getSubDirectory(), getRepository().getAuthManager());
            cachedBranchItems = dirItems.stream()
                    .filter((entry) -> !entry.getName().isEmpty())
                    .collect(Collectors.toList());
        }
        return cachedBranchItems;
    }

    @Override
    protected final Collection<String> getChildrenPIDs() {
        try {
            return getBranchItems().stream()
                    .filter((entry) -> !entry.getName().isEmpty())
                    .map(SVNDirEntry::getName)
                    .sorted(BinarySource.VERSION_SORTER.reversed())
                    .collect(Collectors.toList());
        } catch (SVNException e) {
            SVNErrorCode code = e.getErrorMessage().getErrorCode();
            if (code == SVNErrorCode.RA_SVN_IO_ERROR || code == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                return super.getChildrenPIDs().stream().sorted(BinarySource.VERSION_SORTER.reversed()).collect(Collectors.toList());
            } else {
                throw new Error(e);
            }
        }
    }

    private Repository getRepository() {
        return (Repository) this.getOwner();
    }

}
