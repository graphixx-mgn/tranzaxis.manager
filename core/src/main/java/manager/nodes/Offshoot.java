package manager.nodes;

import codex.config.ConfigStoreService;
import codex.config.IConfigStoreService;
import codex.explorer.tree.INode;
import codex.model.Access;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.type.Str;
import codex.utils.ImageUtils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import manager.commands.offshoot.BuildWC;
import manager.commands.offshoot.DeleteWC;
import manager.commands.offshoot.RefreshWC;
import manager.commands.offshoot.RunDesigner;
import manager.commands.offshoot.UpdateWC;
import manager.svn.SVN;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.*;
import javax.swing.*;

public class Offshoot extends BinarySource {

    public final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    public final static ImageIcon ICON = ImageUtils.getByPath("/images/branch.png");

    public final static String PROP_WC_STATUS   = "wcStatus";
    public final static String PROP_WC_REVISION = "wcRevision";
    public final static String PROP_WC_BUILT    = "built";
    public final static String PROP_WC_LOADED   = "loaded";

    static {
        CommandRegistry.getInstance().registerCommand(DeleteWC.class);
        CommandRegistry.getInstance().registerCommand(RefreshWC.class);
        CommandRegistry.getInstance().registerCommand(UpdateWC.class);
        CommandRegistry.getInstance().registerCommand(BuildWC.class);
        CommandRegistry.getInstance().registerCommand(RunDesigner.class);
    }

    public Offshoot(EntityRef owner, String title) {
        super(owner, ICON, title);
        
        // Properties
        model.addDynamicProp(PROP_WC_STATUS,   new Enum(WCStatus.Absent), Access.Edit, () -> {
            if (this.getOwner() != null) {
                return getWorkingCopyStatus();
            } else {
                return WCStatus.Absent;
            }
        });
        model.addDynamicProp(PROP_WC_REVISION, new Str(null), null, () -> {
            WCStatus status = getWCStatus();
            if ((status.equals(WCStatus.Succesfull) || status.equals(WCStatus.Erroneous)) && SVNWCUtil.isVersionedDirectory(new File(getLocalPath()))) {
                return getWorkingCopyRevision(false).getNumber()+" / "+DATE_FORMAT.format(getWorkingCopyRevisionDate(false));
            } else {
                return null;
            }
        }, PROP_WC_STATUS);

        model.addUserProp(PROP_WC_BUILT,       new BuildStatus(), false, null);
        model.addUserProp(PROP_WC_LOADED,      new Bool(null), false, Access.Any);
    }
    
    public final String getVersion() {
        return getPID();
    }
    
    public final WCStatus getWCStatus() {
        WCStatus wcStatus = (WCStatus) model.getValue(PROP_WC_STATUS);
        SwingUtilities.invokeLater(() -> setMode(wcStatus.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
        return wcStatus;
    }
    
    public final boolean isWCLoaded() {
        return model.getValue(PROP_WC_LOADED) == Boolean.TRUE;
    }
    
    public final BuildStatus getBuiltStatus() {
        List<String> value = (List<String>) model.getValue(PROP_WC_BUILT);
        if (value != null) {
            BuildStatus status = new BuildStatus();
            status.setValue(value);
            return status;
        }
        return null;
    }
    
    public final Offshoot setBuiltStatus(BuildStatus value) {
        model.setValue(PROP_WC_BUILT, value);
        return this;
    }
    
    public final Offshoot setWCLoaded(boolean value) {
        model.setValue(PROP_WC_LOADED, value);
        return this;
    }

    @Override
    protected Class<? extends RepositoryBranch> getParentClass() {
        return Development.class;
    }

    public final List<String> getJvmDesigner() {
        if (getParent() != null) {
            return ((Development) getParent()).getJvmDesigner();
        } else {
            IConfigStoreService CAS = (IConfigStoreService) ServiceRegistry.getInstance().lookupService(ConfigStoreService.class);
            Development dev = CAS.findReferencedEntries(Repository.class, getRepository().getID()).stream()
                    .filter(link -> link.entryClass.equals(Development.class.getCanonicalName()))
                    .map(link -> (Development) EntityRef.build(Development.class, link.entryID).getValue())
                    .findFirst()
                    .orElse(Entity.newPrototype(Development.class));
            return dev.getJvmDesigner();
        }
    }
    
    public final WCStatus getWorkingCopyStatus() {
        String   wcPath = getLocalPath();
        WCStatus status;

        final File localDir = new File(wcPath);
        if (!localDir.exists()) {
            status = WCStatus.Absent;
        } else if (!SVNWCUtil.isVersionedDirectory(localDir)) {
            status = WCStatus.Invalid;
        } else {
            ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
            SVNInfo info = SVN.info(wcPath, false, authMgr);
            if (
                    info == null || 
                    info.getCommittedDate() == null || 
                    info.getCommittedRevision() == null ||
                    info.getCommittedRevision() == SVNRevision.UNDEFINED
            ) {
                status = WCStatus.Interrupted;
            } else {
                status = WCStatus.Succesfull;

//                final SVNClientManager clientMgr = SVNClientManager.newInstance(new DefaultSVNOptions(), authMgr);
//                try {
//                    System.out.println("Check "+wcPath);
//                    clientMgr.getStatusClient().doStatus(new File(wcPath), SVNRevision.HEAD, SVNDepth.INFINITY, false, false, false, false, new ISVNStatusHandler() {
//                        @Override
//                        public void handleStatus(SVNStatus svnStatus) throws SVNException {
//                            if (svnStatus.isConflicted()) {
//                                System.out.println("path=" + svnStatus.getFile() + ", conflict=" + svnStatus.isConflicted());
//                                System.out.println("node="+svnStatus.getNodeStatus()+", content="+svnStatus.getContentsStatus());
//                                System.out.println("conflict="+svnStatus.getTreeConflict());
//                            }
//                        }
//                    }, null);
//                } catch (SVNException e) {
//                    Logger.getLogger().warn("SVN operation ''status'' error: {0}", e.getErrorMessage());
//                }
//                if (SVN.status(wcPath, false, authMgr).isConflicted()) {
//                    status = WCStatus.Erroneous;
//                } else {
//                    status = WCStatus.Succesfull;
//                }
            }
        }
        return status;
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
    
}