package manager.commands;

import codex.command.EntityCommand;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.log.Logger;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ITaskExecutorService;
import codex.task.TaskManager;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import manager.nodes.Offshoot;
import manager.svn.SVN;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;


public class UpdateWC extends EntityCommand {
    
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);

    public UpdateWC() {
        super(
                "update", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/update.png"), 28, 28), 
                Language.get("desc"), 
                (entity) -> {
                    return !entity.model.getValue("wcStatus").equals(WCStatus.Invalid);
                }
        );
    }

    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class)).executeTask(
                new UpdateTask((Offshoot) entity)
        );
    }
    
    private class UpdateTask extends AbstractTask<Void> {

        private final Offshoot offshoot;

        public UpdateTask(Offshoot offshoot) {
            super(Language.get(UpdateWC.class.getSimpleName(), "title") + ": "+offshoot.getWCPath());
            this.offshoot = offshoot;
        }

        @Override
        public Void execute() throws Exception {
            String wcPath = offshoot.getWCPath();
            String repoUrl = Entity.getOwner(offshoot).model.getValue("repoUrl")+"/dev/"+offshoot.model.getPID();
            
            setProgress(0, Language.get(UpdateWC.class.getSimpleName(), "command@calc"));
            Long changes = SVN.diff(wcPath, repoUrl, SVNRevision.HEAD, null, null);
            if (changes > 0) {
                Logger.getLogger().info("Total number of changes of working copy ''{0}'': {1}", new Object[] {wcPath, changes});
                Logger.getLogger().info(
                        "Start update working copy\n"+
                        "                     * Remote URL: ''{0}''\n"+
                        "                     * Local path: ''{1}''", 
                        new Object[]{repoUrl, wcPath}
                );
                AtomicInteger loaded   = new AtomicInteger(0);
                AtomicInteger added    = new AtomicInteger(0);
                AtomicInteger deleted  = new AtomicInteger(0);
                AtomicInteger restored = new AtomicInteger(0);
                AtomicInteger changed  = new AtomicInteger(0);
                
                SVN.update(repoUrl, wcPath, SVNRevision.HEAD, null, null, new ISVNEventHandler() {
                        @Override
                        public void handleEvent(SVNEvent event, double d) throws SVNException {
                            if (event.getAction() != SVNEventAction.UPDATE_STARTED && event.getAction() != SVNEventAction.UPDATE_COMPLETED) {
                                // Условие ниже требуется для обновления с R1 до R2 и checkout
                                if (event.getNodeKind() != SVNNodeKind.DIR) {
                                    loaded.addAndGet(1);
                                    int percent = (int) (loaded.get() * 100 / changes);
                                    SwingUtilities.invokeLater(() -> {
                                        setProgress(
                                                percent > 100 ? 100 : percent, 
                                                Language.get(UpdateWC.class.getSimpleName(), "command@progress")+event.getFile().getPath().replace(wcPath+File.separator, "")
                                        );
                                    });
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
                            if (UpdateTask.this.isCancelled()) {
                                throw new SVNCancelException();
                            }
                        }
                    }
                );
                
                Logger.getLogger().info(
                        "Update working copy finished\n"+
                        (added.get()    == 0 ? "" : "                     * Added:    {0}\n")+
                        (deleted.get()  == 0 ? "" : "                     * Deleted:  {2}\n")+
                        (restored.get() == 0 ? "" : "                     * Restored: {1}\n")+
                        (changed.get()  == 0 ? "" : "                     * Changed:  {1}\n")+
                                                    "                     * Total:    {3}", 
                        new Object[]{added.get(), changed.get(), deleted.get(), loaded.get()}
                );
            } else {
                Logger.getLogger().info("No need to update working copy: {0}", wcPath);
            }
            return null;
        }

        @Override
        public void finished(Void res) {
            WCStatus status = offshoot.getStatus();
            offshoot.model.setValue("wcStatus", status);
            offshoot.model.setValue("loaded",  !status.equals(WCStatus.Absent));
            offshoot.model.commit();
            offshoot.setMode(INode.MODE_SELECTABLE + (status.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
        }
    
    }
}
