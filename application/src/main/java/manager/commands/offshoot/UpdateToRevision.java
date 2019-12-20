package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.command.ValueProvider;
import codex.mask.IMask;
import codex.supplier.RowSelector;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.type.IComplexType;
import codex.type.Int;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.offshoot.build.BuildKernelTask;
import manager.commands.offshoot.build.BuildSourceTask;
import manager.commands.offshoot.revision.RevisionSupplier;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Map;

@EntityCommand.Definition(parentCommand = RefreshWC.class)
public class UpdateToRevision extends EntityCommand<Offshoot> {

    private final static String PARAM_REVISION = "revision";

    private final static ImageIcon ICON_COMMAND = ImageUtils.combine(
            ImageUtils.getByPath("/images/folder.png"),
            ImageUtils.createBadge("R.", Color.decode("#3399FF"), Color.WHITE),
            SwingConstants.SOUTH_EAST
    );

    public UpdateToRevision() {
        super(
                "update to revision",
                Language.get("title"),
                ICON_COMMAND,
                Language.get("title"),
                (offshoot) ->
                        !offshoot.getWCStatus().equals(WCStatus.Invalid)  &&
                        offshoot.getRepository().isRepositoryOnline(false)
        );

        setParameters(
                new PropertyHolder<>(PARAM_REVISION, new Int(null), true)
        );
    }

    @Override
    protected void preprocessParameters(ParamModel paramModel) {
        Offshoot offshoot = getContext().get(0);

        RowSelector<String> selector = RowSelector.Single.newInstance(new RevisionSupplier(getContext().get(0)), true);
        ValueProvider<String> provider = new ValueProvider<String>(selector) {
            @Override
            public void execute(PropertyHolder<IComplexType<String, IMask<String>>, String> context) {
                if (SVNWCUtil.isVersionedDirectory(new File(offshoot.getLocalPath())) && offshoot.getWCStatus() != WCStatus.Absent) {
                    SVNRevision revision = offshoot.getWorkingCopyRevision(false);
                    setValue(selector.select(String.valueOf(revision.getNumber())));
                } else {
                    setValue(selector.select(null));
                }
            }

            @Override
            public void setValue(String value) {
                if (value != null) {
                    paramModel.setValue(PARAM_REVISION, Integer.valueOf(value));
                }
            }
        };
        paramModel.getEditor(PARAM_REVISION).addCommand(provider);
    }

    @Override
    public ITask getTask(Offshoot context, Map<String, IComplexType> params) {
        return new GroupTask<>(
                Language.get("title") + ": "+(context).getLocalPath(),
                new UpdateWC.UpdateTask(context, SVNRevision.create(((Int) params.get(PARAM_REVISION)).getValue())),
                context.new CheckConflicts()
        );
    }

    @Override
    public void execute(Offshoot context, Map<String, IComplexType> params) {}
}
