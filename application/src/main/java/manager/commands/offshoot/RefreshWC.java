package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.property.PropertyHolder;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.text.MessageFormat;
import java.util.Map;
import manager.commands.offshoot.build.BuildKernelTask;
import manager.commands.offshoot.build.BuildSourceTask;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import javax.swing.*;

public class RefreshWC extends EntityCommand<Offshoot> {

    private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
            ImageUtils.combine(
                ImageUtils.getByPath("/images/folder.png"),
                ImageUtils.resize(ImageUtils.getByPath("/images/build.png"), .7f),
                SwingConstants.SOUTH_EAST
            ),
            ImageUtils.resize(ImageUtils.getByPath("/images/up.png"), .7f),
            SwingConstants.SOUTH_WEST
    );

    private static final String PARAM_CLEAN = "clean";

    public RefreshWC() {
        super(
                "refresh",
                Language.get("title"),
                COMMAND_ICON,
                Language.get("desc"), 
                (offshoot) ->
                        !offshoot.getWCStatus().equals(WCStatus.Erroneous) &&
                        offshoot.getRepository().isRepositoryOnline(false)
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
    public ITask getTask(Offshoot context, Map<String, IComplexType> params) {
        return new GroupTask(
                MessageFormat.format(
                        "{0}: ''{1}/{2}''",
                        Language.get("title"),
                        context.getRepository().getPID(),
                        context.getPID()
                ),
                new UpdateWC.UpdateTask(context, SVNRevision.HEAD),
                context.new CheckConflicts(),
                new BuildKernelTask(context),
                new BuildSourceTask(context, params.get(PARAM_CLEAN).getValue() == Boolean.TRUE)
        );
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> map) {}
    
}
