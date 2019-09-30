package manager.nodes;

import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.type.EntityRef;
import manager.svn.SVN;
import org.atteo.classindex.IndexSubclasses;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import javax.swing.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@IndexSubclasses
@EntityDefinition(autoGenerated = true)
@RepositoryBranch.Branch(remoteDir = "", localDir="", hasArchive = false)
public abstract class RepositoryBranch extends Catalog {

    static String ARCHIVE_DIR = "archive";

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Branch {
        String remoteDir();
        String localDir();
        boolean hasArchive();
    }

    RepositoryBranch(EntityRef owner, ImageIcon icon, String title, String hint) {
        super(owner, icon, title, hint);
    }

    private List<String> getDirectories() {
        if (this.getClass().getAnnotation(Branch.class).hasArchive() && getRepository().isLoadArchive()) {
            return Arrays.asList(
                    this.getClass().getAnnotation(Branch.class).remoteDir(),
                    ARCHIVE_DIR + "/" + this.getClass().getAnnotation(Branch.class).remoteDir()
            );
        } else {
            return Collections.singletonList(this.getClass().getAnnotation(Branch.class).remoteDir());
        }
    }

    public abstract void loadBranch();

    public abstract void unloadBranch();

    @Override
    public abstract Class<? extends Entity> getChildClass();

    @Override
    public final Map<Class<? extends Entity>, Collection<String>> getChildrenPIDs() {
        Map<Class<? extends Entity>, Collection<String>> PIDs = super.getChildrenPIDs();
        PIDs.put(
                getChildClass(),
                Stream.concat(
                    super.getChildrenPIDs().get(getChildClass()).stream(),
                    getRepository().isLocked(true) ?
                            getDirectories().stream()
                                .map(subDir -> {
                                    try {
                                        return SVN.list(getRepository().getRepoUrl() + "/" + subDir, getRepository().getAuthManager());
                                    } catch (SVNException e) {
                                        return new LinkedList<SVNDirEntry>();
                                    }
                                })
                                .flatMap(Collection::stream)
                                .filter(svnDirEntry -> !svnDirEntry.getName().isEmpty())
                                .map(SVNDirEntry::getName) :
                            Stream.empty()
                )
                .distinct()
                .sorted(BinarySource.VERSION_SORTER.reversed())
                .collect(Collectors.toList())
        );
        return PIDs;
    }

    Repository getRepository() {
        return (Repository) this.getOwner();
    }

}
