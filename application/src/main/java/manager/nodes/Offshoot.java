package manager.nodes;

import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Access;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.offshoot.*;
import manager.svn.SVN;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.apache.commons.io.FileDeleteStrategy;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.*;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Offshoot extends BinarySource {

    public final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    public final static ImageIcon ICON = ImageUtils.getByPath("/images/branch.png");
    public final static String PROP_WC_STATUS   = "wcStatus";
    public final static String PROP_WC_REVISION = "wcRevision";
    public final static String PROP_WC_BUILT    = "built";
    public final static String PROP_WC_LOADED   = "loaded";

    static {
        CommandRegistry.getInstance().registerCommand(RefreshWC.class);
        CommandRegistry.getInstance().registerCommand(UpdateWC.class);
        CommandRegistry.getInstance().registerCommand(UpdateToRevision.class);
        CommandRegistry.getInstance().registerCommand(BuildWC.class);
        CommandRegistry.getInstance().registerCommand(RunDesigner.class);
        CommandRegistry.getInstance().registerCommand(DebugProfile.class);
    }

    public Offshoot(EntityRef owner, String title) {
        super(owner, ICON, title);
        
        // Properties
        model.addDynamicProp(PROP_WC_STATUS, new Enum<>(WCStatus.Absent), Access.Edit, () -> {
            if (this.getOwner() != null && new File(getLocalPath()).exists()) {
                return getWorkingCopyStatus();
            } else {
                return WCStatus.Absent;
            }
        });
        model.addDynamicProp(PROP_WC_REVISION, new Str(null), null, () -> {
            WCStatus status = getWCStatus();
            if (status.isOperative() || status.equals(WCStatus.Unknown)) {
                SVNRevision revision = getWorkingCopyRevision(false);
                Date date = getWorkingCopyRevisionDate(false);
                if (!SVNRevision.UNDEFINED.equals(revision) && date != null) {
                    return MessageFormat.format("{0} / {1}", String.valueOf(revision.getNumber()), DATE_FORMAT.format(date));
                }
            }
            return null;
        }, PROP_WC_STATUS);
        model.addUserProp(PROP_WC_BUILT, new BuildStatus(), false, null);

        PropertyHolder<Bool, Boolean> propLoaded = new PropertyHolder<Bool, Boolean>(PROP_WC_LOADED, new Bool(null), false) {
            @Override
            public boolean isValid() {
                return (getID() == null) == getWCStatus().equals(WCStatus.Absent);
            }
        };
        model.addUserProp(propLoaded, Access.Any);
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        if (parent != null) {
            WCStatus status = getWCStatus();
            if (status.isOperative()) {
                checkConflicts();
            }
        }
    }

    public final String getVersion() {
        return getPID();
    }

    public final void setWCStatus(WCStatus wcStatus) {
        model.setValue(PROP_WC_STATUS, wcStatus);
    }
    
    public final WCStatus getWCStatus() {
        WCStatus wcStatus = (WCStatus) model.getValue(PROP_WC_STATUS);
        setMode(wcStatus.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED);
        return wcStatus;
    }

    private void checkConflicts() {
        CheckConflicts checkTask = new CheckConflicts();
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).enqueueTask(checkTask);
    }

    public final void setWCLoaded(boolean value) {
        model.setValue(PROP_WC_LOADED, value);
    }
    
    final boolean isWCLoaded() {
        return model.getValue(PROP_WC_LOADED) == Boolean.TRUE;
    }
    
    @SuppressWarnings("unchecked")
    final BuildStatus getBuiltStatus() {
        List<String> value = (List<String>) model.getValue(PROP_WC_BUILT);
        if (value != null) {
            BuildStatus status = new BuildStatus();
            status.setValue(value);
            return status;
        }
        return null;
    }
    
    public final void setBuiltStatus(BuildStatus value) {
        model.setValue(PROP_WC_BUILT, value);
    }

    @Override
    protected Class<? extends RepositoryBranch> getParentClass() {
        return Development.class;
    }

    public final List<String> getJvmDesigner() {
        if (getParent() != null) {
            return ((Development) getParent()).getJvmDesigner();
        } else {
            IConfigStoreService CAS = ServiceRegistry.getInstance().lookupService(IConfigStoreService.class);
            Development dev = CAS.findReferencedEntries(Repository.class, getRepository().getID()).stream()
                    .filter(link -> link.entryClass.equals(Development.class.getCanonicalName()))
                    .map(link -> EntityRef.build(Development.class, link.entryID).getValue())
                    .findFirst()
                    .orElse(Entity.newPrototype(Development.class));
            return dev.getJvmDesigner();
        }
    }
    
    private WCStatus getWorkingCopyStatus() {
        String wcPath = getLocalPath();
        final File localDir = new File(wcPath);
        if (!localDir.exists()) {
            return WCStatus.Absent;
        } else if (!SVNWCUtil.isVersionedDirectory(localDir)) {
            return WCStatus.Invalid;
        } else {
            ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
            SVNInfo info = SVN.info(wcPath, false, authMgr);
            if (
                    info == null ||
                    info.getCommittedDate() == null ||
                    info.getCommittedRevision() == null ||
                    info.getCommittedRevision() == SVNRevision.UNDEFINED
            ) {
                return WCStatus.Interrupted;
            } else {
                return WCStatus.Successful;
            }
        }
    }
    
    public final SVNRevision getWorkingCopyRevision(boolean remote) {
        String wcPath = remote ? getRemotePath(): getLocalPath();
        ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
        SVNInfo info = SVN.info(wcPath, remote, authMgr);
        return info == null ? SVNRevision.UNDEFINED : info.getCommittedRevision();
    }
    
    public final Date getWorkingCopyRevisionDate(boolean remote) {
        String wcPath = remote ? getRemotePath() : getLocalPath();
        ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
        SVNInfo info = SVN.info(wcPath, remote, authMgr);
        return info == null ? null : info.getCommittedDate();
    }

    @Override
    protected void remove() {
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).executeTask((this).new DeleteOffshoot() {
            @Override
            public void finished(Void result) {
                SwingUtilities.invokeLater(() -> {
                    if (!isCancelled() && Offshoot.this.getWorkingCopyStatus() == WCStatus.Absent) {
                        Offshoot.super.remove();
                    } else {
                        Offshoot.this.model.read();
                    }
                });
            }
        });
    }

    public class DeleteOffshoot extends AbstractTask<Void> {

        public DeleteOffshoot() {
            super(Language.get(Offshoot.class, "delete@task.title") + ": "+Offshoot.this.getLocalPath());
            addListener(new ITaskListener() {
                @Override
                public void beforeExecute(ITask task) {
                    if (!Offshoot.this.islocked()) {
                        try {
                            Offshoot.this.getLock().acquire();
                        } catch (InterruptedException e) {
                            //
                        }
                    }
                }

                @Override
                public void afterExecute(ITask task) {
                    Offshoot.this.getLock().release();
                }
            });
        }

        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            String wcPath = Offshoot.this.getLocalPath();
            if (!new File(wcPath).exists()) {
                return null;
            }

            Offshoot.this.setWCLoaded(false);
            Offshoot.this.model.commit(false);

            setProgress(0, Language.get(Offshoot.class, "delete@task.calc"));
            long totalFiles = Files.walk(Paths.get(wcPath)).count();

            AtomicInteger processed = new AtomicInteger(0);
            Files.walkFileTree(Paths.get(wcPath), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    return processPath(file);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    return processPath(dir);
                }

                private FileVisitResult processPath(Path path) {
                    checkPaused();
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    try {
                        FileDeleteStrategy.NORMAL.delete(path.toFile());
                        processed.addAndGet(1);
                        String fileName = path.toString().replace(wcPath + File.separator, "");
                        setProgress(
                                (int) (processed.get() * 100 / totalFiles),
                                MessageFormat.format(
                                        Language.get(Offshoot.class, "delete@task.progress"),
                                        fileName
                                )
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get(wcPath).getParent())) {
                if (!dirStream.iterator().hasNext()) {
                    FileDeleteStrategy.NORMAL.delete(new File(wcPath).getParentFile());
                }
            }
            return null;
        }

        @Override
        public void finished(Void result) {}
    }


    public class CheckConflicts extends AbstractTask<WCStatus> {

        public CheckConflicts() {
            super(Language.get(Offshoot.class, "conflicts@task.title") + ": "+Offshoot.this.getLocalPath());
        }

        @Override
        public WCStatus execute() {
            boolean locked = islocked();
            try {
                if (!locked) getLock().acquire();
                setWCStatus(WCStatus.Unknown);

                List<SVNStatus> statusList = SVN.status(
                        getLocalPath(), false, SVNRevision.WORKING,
                        getRepository().getAuthManager()
                );
                List<File> conflictList = statusList.stream()
                        .filter(svnStatus -> {
                            if (svnStatus.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
                                try {
                                    SVN.resolve(svnStatus.getFile(), getRepository().getAuthManager());
                                    Logger.getLogger().info("Property conflicts in ''{0}'' has been automatically resolved", svnStatus.getFile());
                                } catch (SVNException e) {
                                    Logger.getLogger().info("Property conflicts in ''{0}'' has not been resolved", svnStatus.getFile());
                                    return true;
                                }
                                return false;
                            }
                            return svnStatus.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED;
                        })
                        .map(SVNStatus::getFile)
                        .collect(Collectors.toList());

                if (!conflictList.isEmpty()) {
                    Logger.getLogger().warn(
                            "Working copy ''{0}/{1}'' has file conflicts:\n{2}",
                            getRepository().getTitle(),
                            getTitle(),
                            conflictList.stream()
                                .map(file -> MessageFormat.format(" * {0}", file.getAbsolutePath().replace(getLocalPath(), "")))
                                .collect(Collectors.joining("\n"))
                    );
                    return WCStatus.Erroneous;
                }
            } catch (InterruptedException ignore) {
                //
            } finally {
                if (!locked) getLock().release();
            }
            return null;
        }

        @Override
        public void finished(WCStatus result) {
            if (result != null) {
                Offshoot.this.setWCStatus(result);
            } else {
                Offshoot.this.setWCStatus(getWorkingCopyStatus());
            }
        }
    }
}