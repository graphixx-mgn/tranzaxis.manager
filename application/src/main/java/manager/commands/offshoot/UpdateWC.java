package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.task.AbstractTask;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import manager.nodes.Offshoot;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;

@EntityCommand.Definition(parentCommand = RefreshWC.class)
public class UpdateWC extends EntityCommand<Offshoot> {

    public UpdateWC() {
        super(
                "update",
                Language.get("title"),
                ImageUtils.getByPath("/images/update.png"),
                Language.get("desc"), 
                (offshoot) -> !offshoot.getWCStatus().equals(WCStatus.Invalid)
        );
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> map) {
        executeTask(offshoot, new UpdateTask(offshoot, SVNRevision.HEAD), false);
    }
    
    public static class UpdateTask extends AbstractTask<Void> {

        private final Offshoot    offshoot;
        private final SVNRevision revision;

        UpdateTask(Offshoot offshoot, SVNRevision revision) {
            super(MessageFormat.format(
                    Language.get(UpdateWC.class, "task@title"),
                    offshoot.getLocalPath(),
                    revision
            ));
            this.offshoot = offshoot;
            this.revision = revision;
        }
        
        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            if (!offshoot.getRepository().isRepositoryOnline(true)) return null;

            String wcPath  = offshoot.getLocalPath();
            String repoUrl = offshoot.getRemotePath();
            SVNRevision R1 = offshoot.getWorkingCopyRevision(false);
            String strR1 = SVNRevision.UNDEFINED.equals(R1) ? "<unknown>" : MessageFormat.format(
                    "{0} / {1}",
                    R1, Offshoot.DATE_FORMAT.format(offshoot.getWorkingCopyRevisionDate(false))
            );
            ISVNAuthenticationManager authMgr = offshoot.getRepository().getAuthManager();

            setProgress(0, Language.get(UpdateWC.class, "command@calc"));
            try {
                List<Path> changes = SVN.changes(wcPath, repoUrl, revision, authMgr, new ISVNEventHandler() {
                    @Override
                    public void handleEvent(SVNEvent event, double d) {
                        if (event.getErrorMessage() != null && event.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CLEANUP_REQUIRED) {
                            if (event.getExpectedAction() == SVNEventAction.RESOLVER_STARTING) {
                                Logger.getLogger().info("UPDATE [{0}] perform automatic cleanup", wcPath);
                            } else {
                                Logger.getLogger().info("UPDATE [{0}] continue after cleanup", wcPath);
                            }
                        }
                    }

                    @Override
                    public void checkCancelled() throws SVNCancelException {
                        checkPaused();
                        if (UpdateTask.this.isCancelled()) {
                            throw new SVNCancelException();
                        }
                    }
                });
                if (changes.size() > 0) {
                    Logger.getLogger().debug("Found changes of branch ''{0}'': {1}", wcPath, changes.size());

                    offshoot.setWCLoaded(false);
                    offshoot.model.commit(false);
                    
                    Logger.getLogger().info(
                            "UPDATE [{0}] started", 
                            wcPath
                    );
                    AtomicInteger loaded   = new AtomicInteger(0);
                    AtomicInteger added    = new AtomicInteger(0);
                    AtomicInteger deleted  = new AtomicInteger(0);
                    AtomicInteger restored = new AtomicInteger(0);
                    AtomicInteger changed  = new AtomicInteger(0);

                    long total = changes.size();
                    SVN.update(repoUrl, wcPath, revision, authMgr, new ISVNEventHandler() {
                            @Override
                            public void handleEvent(SVNEvent event, double d) {
                                if (event.getAction() != SVNEventAction.UPDATE_STARTED && event.getAction() != SVNEventAction.UPDATE_COMPLETED) {
                                    if (changes.contains(event.getFile().toPath())) {
                                        loaded.addAndGet(1);
                                        int percent = (int) (loaded.get() * 100 / total);
                                        setProgress(
                                                Math.min(percent, 100),
                                                MessageFormat.format(
                                                        Language.get(UpdateWC.class, "command@progress"),
                                                        event.getFile().getPath().replace(wcPath+File.separator, "")
                                                )
                                        );
                                        SVNEventAction action = event.getAction();
                                        if (action == SVNEventAction.UPDATE_ADD || action == SVNEventAction.UPDATE_SHADOWED_ADD) {
                                            added.addAndGet(1);
                                        } else if (action == SVNEventAction.UPDATE_DELETE || action == SVNEventAction.UPDATE_SHADOWED_DELETE) {
                                            deleted.addAndGet(1);
                                        } else if (action == SVNEventAction.UPDATE_UPDATE || action == SVNEventAction.UPDATE_SHADOWED_UPDATE) {
                                            changed.addAndGet(1);
                                        } else if (action == SVNEventAction.RESTORE) {
                                            restored.addAndGet(1);
                                        }
                                    } else {
                                        if (event.getFile().isFile()) System.out.println(event);
                                    }
                                }
                            }

                            @Override
                            public void checkCancelled() throws SVNCancelException {
                                checkPaused();
                                if (UpdateTask.this.isCancelled()) {
                                    throw new SVNCancelException();
                                }
                            }
                        }
                    );
                    String strR2 = MessageFormat.format(
                            "{0} / {1}",
                            offshoot.getWorkingCopyRevision(false),
                            Offshoot.DATE_FORMAT.format(offshoot.getWorkingCopyRevisionDate(false))
                    );
                    Logger.getLogger().info(
                            "UPDATE [{0}] finished\nRevision: {1} -> {2}\n"+
                            (added.get()    == 0 ? "" : " * Added:    {3}\n")+
                            (deleted.get()  == 0 ? "" : " * Deleted:  {4}\n")+
                            (restored.get() == 0 ? "" : " * Restored: {5}\n")+
                            (changed.get()  == 0 ? "" : " * Changed:  {6}\n")+
                                                        " * Total:    {7}", 
                            wcPath, strR1, strR2, added.get(), deleted.get(), restored.get(), changed.get(), loaded.get()
                    );
                } else {
                    Logger.getLogger().info("UPDATE [{0}] finished. working copy already actual", wcPath);
                }
            } catch (SVNException e) {
                Optional<Throwable> rootCause = Stream
                        .iterate(e, Throwable::getCause)
                        .filter(element -> element.getCause() == null)
                        .findFirst();
                if (rootCause.isPresent()) {
                    if (rootCause.get() instanceof SVNCancelException || rootCause.get() instanceof ClosedChannelException) {
                        Logger.getLogger().info("UPDATE [{0}] canceled", wcPath);
                    } else {
                        throw e;
                    }
                }
            }
            return null;
        }

        @Override
        public void finished(Void res) {
            SwingUtilities.invokeLater(() -> {
                offshoot.model.updateDynamicProps();
                offshoot.setWCLoaded(offshoot.getWCStatus().equals(WCStatus.Successful));
                try {
                    offshoot.model.commit(false);
                } catch (Exception e) {
                    //
                }
            });
        }
    
    }
}
