package manager.nodes;

import codex.explorer.IExplorerAccessService;
import codex.model.Catalog;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import manager.svn.SVN;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import javax.swing.*;
import java.io.File;
import java.util.Comparator;
import java.util.StringJoiner;

public abstract class BinarySource extends Catalog {

    private final static IExplorerAccessService EAS = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class);
    
    static final Comparator<String> VERSION_SORTER = new Comparator<String>() {
        @Override
        public int compare(String prev, String next) {
            if ("trunk".equals(prev)) {
                return 1;
            } else if ("trunk".equals(next)) {
                return -1;
            } else {
                String[] components1 = prev.split("\\.");
                String[] components2 = next.split("\\.");
                int length = Math.min(components1.length, components2.length);
                for(int i = 0; i < length; i++) {
                    int result = new Integer(components1[i]).compareTo(Integer.parseInt(components2[i]));
                    if(result != 0) {
                        return result;
                    }
                }
                return Integer.compare(components1.length, components2.length);
            }
        }
    };

    BinarySource(EntityRef parent, ImageIcon icon, String title) {
        super(parent, icon, title, null);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }
    
    public Repository getRepository() {
        return (Repository) this.getOwner();
    }

    protected abstract Class<? extends RepositoryBranch> getParentClass();

    public final String getLocalPath() {
        String workDir = ((Common) EAS.getRoot()).getWorkDir().toString();
        return new StringJoiner(File.separator)
                .add(workDir)
                .add(getParentClass().getAnnotation(RepositoryBranch.Branch.class).localDir())
                .add(Repository.urlToDirName(getRepository().getRepoUrl()))
                .add(getPID())
                .toString();
    }
    
    public final String getRemotePath() {
        boolean hasArchive = getParentClass().getAnnotation(RepositoryBranch.Branch.class).hasArchive();
        Repository repo = getRepository();
        String PID = getPID();

        StringJoiner defPath = new StringJoiner("/")
                .add(repo.getRepoUrl())
                .add(getParentClass().getAnnotation(RepositoryBranch.Branch.class).remoteDir());

        if (hasArchive) {
            StringJoiner archPath = new StringJoiner("/")
                    .add(repo.getRepoUrl())
                    .add(RepositoryBranch.ARCHIVE_DIR + "/" + getParentClass().getAnnotation(RepositoryBranch.Branch.class).remoteDir());
            ISVNAuthenticationManager authMgr = repo.getAuthManager();

            try {
                if (SVN.list(defPath.toString(), authMgr).stream().anyMatch(svnDirEntry -> svnDirEntry.getName().equals(PID))) {
                    return defPath.add(PID).toString();
                } else if (SVN.list(archPath.toString(), authMgr).stream().anyMatch(svnDirEntry -> svnDirEntry.getName().equals(PID))) {
                    return archPath.add(PID).toString();
                } else {
                    throw new SVNException(SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND));
                }
            } catch (SVNException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return defPath.add(PID).toString();
        }
    }

    final String getStarterPath() {
        return new StringJoiner(File.separator)
                .add(getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("starter")
                .add("bin")
                .add("dist")
                .add("starter.jar")
                .toString();
    }
    
}
