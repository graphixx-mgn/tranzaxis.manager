package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.property.PropertyHolder;
import codex.task.GroupTask;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.util.Map;
import manager.commands.offshoot.build.BuildKernelTask;
import manager.commands.offshoot.build.BuildSourceTask;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class RefreshWC extends EntityCommand<Offshoot> {

    private static final String PARAM_CLEAN = "clean";

    public RefreshWC() {
        super(
                "refresh",
                Language.get("title"),
                ImageUtils.getByPath("/images/rebuild.png"),
                Language.get("desc"), 
                (offshoot) -> !offshoot.getWCStatus().equals(WCStatus.Invalid)
        );
        setParameters(
                new PropertyHolder<>(PARAM_CLEAN, new Bool(Boolean.FALSE), true)
        );
    }
    
    @Override
    public boolean multiContextAllowed() {
        return true;
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> map) {
        if (!context.getRepository().isRepositoryOnline(true)) return;
        executeTask(
                context,
                new GroupTask<>(
                        Language.get("title") + ": "+(context).getLocalPath(),
                        new UpdateWC.UpdateTask(context, SVNRevision.HEAD),
                        context.new CheckConflicts(),
                        new BuildKernelTask(context),
                        new BuildSourceTask(context, map.get(PARAM_CLEAN).getValue() == Boolean.TRUE)
                ), 
                false
        );
    }
    
}
