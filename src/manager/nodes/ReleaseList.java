package manager.nodes;

import codex.model.Catalog;
import codex.type.EntityRef;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

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
        String svnUser = (String) this.model.getOwner().model.getValue("svnUser");
        String svnPass = (String) this.model.getOwner().model.getValue("svnPass");
        
        try {
            List<SVNDirEntry> dirItems = SVN.list(repoUrl+"/releases", svnUser, svnPass);
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
            if (e.getErrorMessage().getErrorCode().getCode() != SVNErrorCode.RA_SVN_MALFORMED_DATA.getCode()) {
                throw new UnsupportedOperationException(Language.get(Repository.class.getSimpleName(), "error@svnerr"));
            } else {
                return super.getChildrenPIDs().stream().sorted(BinarySource.VERSION_SORTER.reversed()).collect(Collectors.toList());
            }
        }
    }
    
}
