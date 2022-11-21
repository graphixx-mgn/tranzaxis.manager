package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.type.EntityRef;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import javax.swing.*;
import java.net.*;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Supplier;

public abstract class BinarySource extends Catalog {

    final static String TRUNK = "trunk";
    
    public static final Comparator<String> VERSION_SORTER = (prev, next) -> {
        if (TRUNK.equals(prev)) {
            return 1;
        } else if (TRUNK.equals(next)) {
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
    };

    BinarySource(EntityRef<? extends RepositoryBranch> parent, ImageIcon icon, String title) {
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

    public final Path getLocalPath() {
        final Supplier<String> host = () -> {
            try {
                return InetAddress.getByName(getRepository().getUrl().getHost()).getHostName();
            } catch (UnknownHostException e) {
                return getRepository().getUrl().getHost();
            }
        };
        final Supplier<String> path = () -> getRepository().getUrl().getPath();
        return Common.getInstance().getWorkDir()
                .resolve(getParentClass().getAnnotation(RepositoryBranch.Branch.class).localDir())
                .resolve(host.get().concat(path.get()).replaceAll("[/\\\\]", "."))
                .resolve(getPID());
    }

    public final Path getRelativePath(Path path) {
        return getLocalPath().relativize(path);
    }
    
    public final SVNURL getRemotePath() {
        try {
            return getRepository().getUrl()
                    .appendPath(getParentClass().getAnnotation(RepositoryBranch.Branch.class).remoteDir(), true)
                    .appendPath(getPID(), true);
        } catch (SVNException e) {
            throw new Error(e);
        }
    }

    final Path getStarterPath() {
        return getLocalPath()
                .resolve("org.radixware")
                .resolve("kernel")
                .resolve("starter")
                .resolve("bin")
                .resolve("dist")
                .resolve("starter.jar");
    }
}