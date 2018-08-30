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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringJoiner;
import manager.commands.BuildWC;
import manager.commands.DeleteWC;
import manager.commands.RefreshWC;
import manager.commands.RunDesigner;
import manager.commands.UpdateWC;
import manager.svn.SVN;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.radixware.kernel.common.repository.Branch;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;


public class Offshoot extends BinarySource {
    
    public final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm");

    public Offshoot(EntityRef parent, String title) {
        super(parent, ImageUtils.getByPath("/images/branch.png"), title);
        
        // Properties
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getPID();
        });
        model.addDynamicProp("wcStatus", new Enum(WCStatus.Absent), Access.Edit, () -> {
            if (this.model.getOwner() != null) {
                return getStatus();
            } else {
                return WCStatus.Absent;
            }
        });
        model.addDynamicProp("wcRev", new Str(null), null, () -> {
            if (this.model.getOwner() != null && getStatus().equals(WCStatus.Succesfull)) {
                return getRevision(false).getNumber()+" / "+DATE_FORMAT.format(getRevisionDate(false));
            } else {
                return null;
            }
        }, "wcStatus");
        model.addUserProp("loaded", new Bool(null), false, Access.Any);
        model.addUserProp("built", new BuildStatus(), false, null);
        
        // Commands
        addCommand(new DeleteWC());
        addCommand(new RefreshWC().setGroupId("update"));
        addCommand(new UpdateWC().setGroupId("update"));
        addCommand(new BuildWC().setGroupId("update"));
        addCommand(new RunDesigner());
    }

    @Override
    public void setParent(INode parent) {
        super.setParent(parent);
        setMode(INode.MODE_SELECTABLE + (getStatus().equals(WCStatus.Absent) ? 0 : INode.MODE_ENABLED));
    }
    
    @Override
    public Class getChildClass() {
        return null;
    }
    
    public final String getUrlPath() {
        return new StringJoiner("/")
            .add((String) this.model.getOwner().model.getValue("repoUrl"))
            .add("dev")
            .add(model.getPID()).toString();
    }
    
    public final String getWCPath() {
        IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
        String workDir = EAS.getRoot().model.getValue("workDir").toString();
        String repoUrl = (String) this.model.getOwner().model.getValue("repoUrl");
        
        StringJoiner wcPath = new StringJoiner(File.separator)
            .add(workDir)
            .add("sources")
            .add(repoUrl.replaceAll("svn(|\\+[\\w]+)://([\\w\\./\\d]+)", "$2").replaceAll("[/\\\\]{1}", "."))
            .add(model.getPID());
        
        return wcPath.toString();
    }
    
    public final WCStatus getStatus() {
        String   wcPath = getWCPath();
        WCStatus status;
        
        final File localDir = new File(wcPath);
        if (!localDir.exists()) {
            status = WCStatus.Absent;
        } else if (!SVNWCUtil.isVersionedDirectory(localDir)) {
            status = WCStatus.Invalid;
        } else {
            SVNInfo info = SVN.info(wcPath, false, null, null);
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
    
    public final SVNRevision getRevision(boolean remote) {
        String wcPath = remote ? getUrlPath() : getWCPath();
        SVNInfo info = SVN.info(wcPath, remote, null, null);
        return info.getCommittedRevision();
    }
    
    public final Date getRevisionDate(boolean remote) {
        String wcPath = remote ? getUrlPath() : getWCPath();
        SVNInfo info = SVN.info(wcPath, remote, null, null);
        return info.getCommittedDate();
    }
    
    public final String getBaseDevUri() {
        try {
            Branch branch = Branch.Factory.loadFromDir(new File(getWCPath()));
            return branch.getBaseDevelopmentLayerUri();
        } catch (IOException e) {
            return null;
        }
    }
    
    public final static WCStatus getStatus(File dir) {
        if (!dir.exists()) {
            return WCStatus.Absent;
        } else if (!SVNWCUtil.isVersionedDirectory(dir)) {
            return WCStatus.Invalid;
        } else {
            SVNInfo info = SVN.info(dir.getPath(), false, null, null);
            if (
                    info == null || 
                    info.getCommittedDate() == null || 
                    info.getCommittedRevision() == null ||
                    info.getCommittedRevision() == SVNRevision.UNDEFINED
            ) {
                return WCStatus.Interrupted;
            } else {
                return WCStatus.Succesfull;
            }
        }
    }
}