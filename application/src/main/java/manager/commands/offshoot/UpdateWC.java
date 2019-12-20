package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.task.AbstractTask;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.nio.channels.ClosedChannelException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import manager.nodes.Offshoot;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.*;
import javax.swing.*;

@EntityCommand.Definition(parentCommand = RefreshWC.class)
public class UpdateWC extends EntityCommand<Offshoot> {

    private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
            ImageUtils.getByPath("/images/folder.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/up.png"), .7f),
            SwingConstants.SOUTH_EAST
    );

    public UpdateWC() {
        super(
                "update",
                Language.get("title"),
                COMMAND_ICON,
                Language.get("desc"), 
                (offshoot) -> offshoot.getRepository().isRepositoryOnline(false)
        );
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public ITask getTask(Offshoot context, Map<String, IComplexType> params) {
        return new GroupTask<>(
                Language.get("title") + ": "+(context).getLocalPath(),
                new UpdateWC.UpdateTask(context, SVNRevision.HEAD),
                context.new CheckConflicts()
        );
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> map) {}
    
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
            String wcPath  = offshoot.getLocalPath();
            String repoUrl = offshoot.getRemotePath();
            SVNRevision R1 = offshoot.getWorkingCopyRevision(false);
            String strR1 = SVNRevision.UNDEFINED.equals(R1) ? "<unknown>" : MessageFormat.format(
                    "{0} / {1}",
                    R1, Offshoot.DATE_FORMAT.format(offshoot.getWorkingCopyRevisionDate(false))
            );
            ISVNAuthenticationManager authMgr = offshoot.getRepository().getAuthManager();

            setProgress(0, Language.get(UpdateWC.class, "command@calc"));

            List<SVNURL> changes = SVN.changes(wcPath, repoUrl, revision, authMgr, new ISVNEventHandler() {
                @Override
                public void handleEvent(SVNEvent event, double d) {
                    if (event.getErrorMessage() != null && event.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CLEANUP_REQUIRED) {
                        String desc = getDescription();
                        if (event.getExpectedAction() == SVNEventAction.RESOLVER_STARTING) {
                            setProgress(0, Language.get(UpdateWC.class, "command@cleanup"));
                            Logger.getLogger().info("UPDATE [{0}] perform automatic cleanup", wcPath);
                        } else {
                            Logger.getLogger().info("UPDATE [{0}] continue after cleanup", wcPath);
                            setProgress(0, desc);
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

            try {
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
                    AtomicInteger skipped  = new AtomicInteger(0);

                    long total = changes.size();
                    SVN.update(repoUrl, wcPath, revision, authMgr, new ISVNEventHandler() {
                            @Override
                            public void handleEvent(SVNEvent event, double d) {
                                if (event.getAction() != SVNEventAction.UPDATE_STARTED && event.getAction() != SVNEventAction.UPDATE_COMPLETED) {
                                    if (event.getNodeKind() == SVNNodeKind.FILE) {
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
                                        } else if (action == SVNEventAction.SKIP_CONFLICTED) {
                                            skipped.addAndGet(1);
                                        } else {
                                            System.err.println(action);
                                        }
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
                            (skipped.get()  == 0 ? "" : " * Skipped:  {7}\n")+
                                                        " * Total:    {8}",
                            wcPath, strR1, strR2, added.get(), deleted.get(), restored.get(), changed.get(), skipped.get(), loaded.get()
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
            offshoot.model.updateDynamicProps();
            WCStatus status = offshoot.getWCStatus();
            offshoot.setWCLoaded(status.equals(WCStatus.Successful));
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {
                //
            }
        }
    
    }
}
