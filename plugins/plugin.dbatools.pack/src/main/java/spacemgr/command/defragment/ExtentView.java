package spacemgr.command.defragment;

import codex.command.EntityGroupCommand;
import codex.editor.IEditorFactory;
import codex.editor.TextView;
import codex.model.Access;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.type.AnyType;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.type.Str;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.Extent;
import spacemgr.command.objects.SegmentType;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ExtentView extends Catalog {

    static {
        CommandRegistry.getInstance().registerCommand(ObjectDefragmentation.class);
    }

    private final static ImageIcon ICON_UNKNOWN = ImageUtils.getByPath("/images/question.png");

    private final static String PROP_OWNER = "owner";
    private final static String PROP_TYPE  = "type";
    private final static String PROP_PART  = "part";
    private final static String PROP_SIZE  = "size";
    private final static String PROP_WRONG_FLAG = "hasProblems";
    private final static String PROP_WRONG_VIEW = "showProblems";

    private static ImageIcon getIcon(String extentType) {
        SegmentType type = SegmentType.byType(extentType);
        return type == null ? ICON_UNKNOWN : type.getIcon();
    }

    private final Extent extent;

    ExtentView(Extent extent) {
        super(
            null,
            getIcon(extent.getType()),
            extent.getName(),
            null
        );
        this.extent = extent;

        model.addDynamicProp(PROP_OWNER, new Str(extent.getOwner()), Access.Edit, null);
        model.addDynamicProp(PROP_TYPE,  new Str(extent.getType()), Access.Edit, null);
        model.addDynamicProp(PROP_PART,  new Str(extent.getPartition()), Access.Edit, null);
        model.addDynamicProp(PROP_SIZE,  new Str(FileUtils.formatFileSize(getSegmentSize())), Access.Select, null);

        // Segment problems
        boolean hasProblems = !getExtent().getSegment().problemsGetter().get().isEmpty();
        model.addUserProp(
                new PropertyHolder<Bool, Boolean>(PROP_WRONG_FLAG, new Bool(hasProblems), true) {
                    @Override
                    public boolean isValid() {
                        return super.isValid() && !getPropValue().getValue();
                    }
                },
                Access.Any
        );

        model.addDynamicProp(
                PROP_WRONG_VIEW,
                new AnyType() {
                    @Override
                    public IEditorFactory<AnyType, Object> editorFactory() {
                        return propHolder -> new TextView(propHolder) {{
                            setBorder(new LineBorder(Color.RED, 1));
                        }};
                    }
                },
                Access.Select,
                () -> MessageFormat.format(
                        "{0}",
                        getExtent().getSegment().problemsGetter().get().stream()
                                .map(problem -> MessageFormat.format(
                                        "<tr><td><font color='red'>&#x26A0;</font></td><td><font color='red'>{0}</font></td></tr>",
                                        problem.getDescription(Language.getLocale())
                                ))
                                .collect(Collectors.joining("<br>"))
                )
        );

        // Handlers
        model.getEditor(PROP_WRONG_VIEW).setVisible(hasProblems);
        model.getProperty(PROP_WRONG_FLAG).addChangeListener((name, oldValue, newValue) -> {
            model.getEditor(PROP_WRONG_VIEW).setVisible(newValue == Boolean.TRUE);
        });
    }

    Extent getExtent() {
        return extent;
    }

    private long getSegmentSize() {
        return getExtent().getSegment().getSize();
    }


    static class ObjectDefragmentation extends EntityGroupCommand<ExtentView> {

        public ObjectDefragmentation() {
            super(
                    "object defragmentation",
                    Language.get(ExtentView.class, "command@run.object"),
                    ImageUtils.getByPath("/images/object.png"),
                    Language.get(ExtentView.class, "command@run.object"),
                    extentView -> extentView.model.isValid() && !((Entity) extentView.getParent()).islocked()
            );
        }

        @Override
        public void execute(List<ExtentView> context, Map<String, IComplexType> params) {
            ITask task = new DefragmentationTask(
                    ((CellView) context.get(0).getParent()).getController(),
                    context.parallelStream()
                            .map(extentView -> extentView.getExtent().getSegment())
                            .distinct()
                            .collect(Collectors.toList())
            );
            ServiceRegistry.getInstance()
                    .lookupService(ITaskExecutorService.class)
                    .quietTask(task);
        }
    }
}
