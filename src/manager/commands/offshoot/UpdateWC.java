package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.task.AbstractTask;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.nio.channels.ClosedChannelException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import manager.nodes.Offshoot;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;


public class UpdateWC extends EntityCommand<Offshoot> {

    public UpdateWC() {
        super(
                "update", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), 28, 28), 
                Language.get("desc"), 
                (offshoot) -> {
                    return !offshoot.getWCStatus().equals(WCStatus.Invalid);
                }
        );
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> map) {
        executeTask(offshoot, new UpdateTask(offshoot), false);
    }
    
    public class UpdateTask extends AbstractTask<Void> {

        private final Offshoot offshoot;

        public UpdateTask(Offshoot offshoot) {
            super(Language.get(UpdateWC.class.getSimpleName(), "title") + ": \""+offshoot.getLocalPath()+"\"");
            this.offshoot = offshoot;
        }
        
        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            String wcPath  = offshoot.getLocalPath();
            String repoUrl = offshoot.getRemotePath();
            ISVNAuthenticationManager authMgr = offshoot.getRepository().getAuthManager();

            setProgress(0, Language.get(UpdateWC.class.getSimpleName(), "command@calc"));
            try {
                Long changes = SVN.diff(wcPath, repoUrl, SVNRevision.HEAD, authMgr, new ISVNEventHandler() {
                    @Override
                    public void handleEvent(SVNEvent event, double d) throws SVNException {}

                    @Override
                    public void checkCancelled() throws SVNCancelException {
                        checkPaused();
                        if (UpdateTask.this.isCancelled()) {
                            throw new SVNCancelException();
                        }
                    }
                });
                if (changes > 0) {
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

                    SVN.update(repoUrl, wcPath, SVNRevision.HEAD, authMgr, new ISVNEventHandler() {
                            @Override
                            public void handleEvent(SVNEvent event, double d) throws SVNException {
                                if (event.getErrorMessage() != null && event.getErrorMessage().getErrorCode() == SVNErrorCode.WC_CLEANUP_REQUIRED) {
                                    if (event.getExpectedAction() == SVNEventAction.RESOLVER_STARTING) {
                                        Logger.getLogger().warn("UPDATE [{0}] perfom recovery", wcPath);
                                    } else {
                                        Logger.getLogger().info("UPDATE [{0}] continue after recovery", wcPath);
                                    }
                                    return;
                                }
                                
                                if (event.getAction() != SVNEventAction.UPDATE_STARTED && event.getAction() != SVNEventAction.UPDATE_COMPLETED) {
                                    if (event.getNodeKind() != SVNNodeKind.DIR) {
                                        loaded.addAndGet(1);
                                        int percent = (int) (loaded.get() * 100 / changes);
                                        setProgress(
                                                percent > 100 ? 100 : percent, 
                                                MessageFormat.format(
                                                        Language.get(UpdateWC.class.getSimpleName(), "command@progress"), 
                                                        event.getFile().getPath().replace(wcPath+File.separator, "")
                                                )
                                        );
                                        SVNEventAction action = event.getAction();
                                        if (action == SVNEventAction.UPDATE_ADD) {
                                            added.addAndGet(1);
                                        } else if (action == SVNEventAction.UPDATE_DELETE) {
                                            deleted.addAndGet(1);
                                        } else if (action == SVNEventAction.UPDATE_UPDATE) {
                                            changed.addAndGet(1);
                                        } else if (action == SVNEventAction.RESTORE) {
                                            restored.addAndGet(1);
                                        } else {
                                            System.err.println(action + " / " + event.getFile().getPath().replace(wcPath+File.separator, ""));
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
                    Logger.getLogger().info(
                            "UPDATE [{0}] finished\n"+
                            (added.get()    == 0 ? "" : "                     * Added:    {1}\n")+
                            (deleted.get()  == 0 ? "" : "                     * Deleted:  {2}\n")+
                            (restored.get() == 0 ? "" : "                     * Restored: {3}\n")+
                            (changed.get()  == 0 ? "" : "                     * Changed:  {4}\n")+
                                                        "                     * Total:    {5}", 
                            wcPath, added.get(), deleted.get(), restored.get(), changed.get(), loaded.get()
                    );
                } else {
                    Logger.getLogger().info(
                            "UPDATE [{0}] finished. working copy already actual", 
                            new Object[]{wcPath}
                    );
                }
            } catch (SVNException e) {
                Optional<Throwable> rootCause = Stream
                        .iterate(e, Throwable::getCause)
                        .filter(element -> element.getCause() == null)
                        .findFirst();
                if (rootCause.get() instanceof SVNCancelException || rootCause.get() instanceof ClosedChannelException) {
                    Logger.getLogger().info(
                            "UPDATE [{0}] canceled", 
                            new Object[]{wcPath}
                    );
                } else {
                    throw e;
                }
            }
            return null;
        }

        @Override
        public void finished(Void res) {
            SwingUtilities.invokeLater(() -> {
                offshoot.model.updateDynamicProps();
                offshoot.setWCLoaded(offshoot.getWCStatus().equals(WCStatus.Succesfull));
                try {
                    offshoot.model.commit(false);
                } catch (Exception e) {}
            });
        }
    
    }
}
