package manager.nodes;

import codex.model.Catalog;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

public class ReleaseList extends Catalog {

    public ReleaseList(EntityRef parent) {
        super(parent, ImageUtils.getByPath("/images/releases.png"), "title", null);
    }

    @Override
    public Class getChildClass() {
        return Release.class;
    }
    
    @Override
    public boolean allowModifyChild() {
        return false;
    };
    
    @Override
    protected Collection<String> getChildrenPIDs() {
        String repoUrl = (String) this.model.getOwner().model.getValue("repoUrl");
        ISVNAuthenticationManager authMgr = ((Repository) this.model.getOwner()).getAuthManager();
        
        try {
            List<SVNDirEntry> dirItems = SVN.list(repoUrl+"/releases", authMgr);
            return dirItems.stream()
                    .filter((entry) -> {
                        return !entry.getName().isEmpty();
                    })
                    .map((entry) -> {
                        return entry.getName();
                    })
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
    
}
