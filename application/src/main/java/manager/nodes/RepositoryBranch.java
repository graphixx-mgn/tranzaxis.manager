package manager.nodes;

import codex.config.IConfigStoreService;
import codex.explorer.IExplorerAccessService;
import codex.model.Catalog;
import codex.model.Entity;
import codex.model.EntityDefinition;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import org.atteo.classindex.IndexSubclasses;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import javax.swing.*;
import java.io.File;
import java.lang.annotation.*;
import java.util.*;

@IndexSubclasses
@EntityDefinition(autoGenerated = true)
@RepositoryBranch.Branch(remoteDir = "", localDir="", hasArchive = false)
public abstract class RepositoryBranch extends Catalog {

    private final static IExplorerAccessService EAS = ServiceRegistry.getInstance().lookupService(IExplorerAccessService.class);
    static String ARCHIVE_DIR = "archive";

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Branch {
        String remoteDir();
        String localDir();
        boolean hasArchive();
    }

    RepositoryBranch(EntityRef<Entity> owner, ImageIcon icon, String title, String hint) {
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
    public void loadChildren() {
        if (getRepository().isRepositoryOnline(false)) {
            getDirectories().stream()
                    .map(subDir -> {
                        try {
                            return getRepository().list(getRepository().getDirUrl(subDir));
                        } catch (SVNException e) {
                            return new LinkedList<SVNDirEntry>();
                        }
                    })
                    .flatMap(Collection::stream)
                    .map(SVNDirEntry::getName)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .sorted(BinarySource.VERSION_SORTER.reversed())
                    .forEach(version -> {
                        Entity child = Entity.newInstance(getChildClass(), getRepository().toRef(), version);
                        attach(child);
                    });
        } else {
            List<Class<? extends Entity>> classCatalog = getClassCatalog();
            if (!classCatalog.isEmpty()) {
                EntityRef<Entity> ownerRef = Entity.findOwner(this);
                IConfigStoreService CSS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
                getClassCatalog().forEach(catalogClass -> {
                    List<Entity> children = new LinkedList<>();
                    List<String> loaded = new LinkedList<>();
                    CSS.readCatalogEntries(ownerRef == null ? null : ownerRef.getId(), catalogClass).forEach(entityRef -> {
                        if (entityRef != null && !childrenList().contains(entityRef.getValue())) {
                            loaded.add(entityRef.getValue().getPID());
                            children.add(entityRef.getValue());
                        }
                    });

                    String workDir  = ((Common) EAS.getRoot()).getWorkDir().toString();
                    File   localDir = new File(new StringJoiner(File.separator)
                            .add(workDir)
                            .add(getClass().getAnnotation(RepositoryBranch.Branch.class).localDir())
                            .add(Repository.urlToDirName(getRepository().getRepoUrl()))
                            .toString()
                    );
                    File[] childDir = localDir.listFiles();
                    if (childDir != null) {
                        Arrays.stream(childDir)
                                .filter(file -> SVNWCUtil.isVersionedDirectory(file) && !loaded.contains(file.getName()))
                                .forEach(file -> {
                                    children.add(Entity.newInstance(catalogClass, getRepository().toRef(), file.getName()));
                                });
                    }

                    children.sort((o1, o2) -> BinarySource.VERSION_SORTER.reversed().compare(o1.getPID(), o2.getPID()));
                    children.forEach(this::attach);
                });
            }
        }
    }

    Repository getRepository() {
        return (Repository) this.getOwner();
    }
}