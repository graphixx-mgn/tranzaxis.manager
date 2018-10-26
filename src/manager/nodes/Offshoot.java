package manager.nodes;

import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.explorer.tree.INode;
import codex.model.Access;
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
import java.util.StringJoiner;
import manager.commands.offshoot.BuildWC;
import manager.commands.offshoot.DeleteWC;
import manager.commands.offshoot.RefreshWC;
import manager.commands.offshoot.RunDesigner;
import manager.commands.offshoot.UpdateWC;
import manager.svn.SVN;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


public class Offshoot extends BinarySource {
    
    public final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    public final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    
    public final static String PROP_VERSION     = "version";
    public final static String PROP_WC_STATUS   = "wcStatus";
    public final static String PROP_WC_REVISION = "wcRevision";
    public final static String PROP_WC_BUILT    = "built";
    public final static String PROP_WC_LOADED   = "loaded";

    public Offshoot(EntityRef owner, String title) {
        super(owner, ImageUtils.getByPath("/images/branch.png"), title);
        
        // Properties
        model.addDynamicProp(PROP_VERSION,     new Str(null), null, () -> {
            return getPID();
        });
        model.addDynamicProp(PROP_WC_STATUS,   new Enum(WCStatus.Absent), Access.Edit, () -> {
            if (this.getOwner() != null) {
                return getWorkingCopyStatus();
            } else {
                return WCStatus.Absent;
            }
        });
        model.addDynamicProp(PROP_WC_REVISION, new Str(null), null, () -> {
            if (this.getOwner() != null && getWCStatus().equals(WCStatus.Succesfull)) {
                return getWorkingCopyRevision(false).getNumber()+" / "+DATE_FORMAT.format(getWorkingCopyRevisionDate(false));
            } else {
                return null;
            }
        }, PROP_WC_STATUS);

        model.addUserProp(PROP_WC_BUILT,       new BuildStatus(), false, null);
        model.addUserProp(PROP_WC_LOADED,      new Bool(null), false, Access.Any);
        
        // Commands
        addCommand(new DeleteWC());
        addCommand(new RefreshWC().setGroupId("update"));
        addCommand(new UpdateWC().setGroupId("update"));
        addCommand(new BuildWC().setGroupId("update"));
        addCommand(new RunDesigner());
    }
    
    public final String getVersion() {
        return (String) model.getValue(PROP_VERSION);
    }
    
    public final WCStatus getWCStatus() {
        return (WCStatus) model.getValue(PROP_WC_STATUS);
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
    public final String getRemotePath() {
        return new StringJoiner("/")
            .add(getRepository().getRepoUrl())
            .add("dev")
            .add(getVersion())
            .toString();
    }
    
    @Override
    public final String getLocalPath() {
        String workDir = ((Common) EAS.getRoot()).getWorkDir().toString();
        String repoUrl = getRepository().getRepoUrl();

        return new StringJoiner(File.separator)
            .add(workDir)
            .add("sources")
            .add(Repository.urlToDirName(repoUrl))
            .add(getVersion())
            .toString();
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
            }
        }
        setMode(INode.MODE_SELECTABLE + (status.equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
        return status;
    }
    
    public final SVNRevision getWorkingCopyRevision(boolean remote) {
        String wcPath = remote ? getRemotePath(): getLocalPath();
        ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
        SVNInfo info = SVN.info(wcPath, remote, authMgr);
        return info.getCommittedRevision();
    }
    
    public final Date getWorkingCopyRevisionDate(boolean remote) {
        String wcPath = remote ? getRemotePath() : getLocalPath();
        ISVNAuthenticationManager authMgr = getRepository().getAuthManager();
        SVNInfo info = SVN.info(wcPath, remote, authMgr);
        return info.getCommittedDate();
    }
    
}