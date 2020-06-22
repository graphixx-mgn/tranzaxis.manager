package spacemgr.command.defragment;

import codex.command.EntityCommand;
import codex.model.Catalog;
import codex.model.CommandRegistry;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.Extent;
import spacemgr.command.objects.Segment;
import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

class CellView extends Catalog implements IFormController.ICellView {

    static {
        CommandRegistry.getInstance().registerCommand(LoadMap.class);
        CommandRegistry.getInstance().registerCommand(RunDefragmentation.class);
    }

    private final IFormController controller;

    CellView(IFormController controller) {
        super(null, null, null, null);
        this.controller = controller;
        Objects.requireNonNull(getSelectorPresentation()).addSelectListener(selected -> {
            controller.getMapView().showExtents(selected.parallelStream()
                    .map(entity -> ((ExtentView) entity).getExtent())
                    .map(Extent::getSegment)
                    .map(Segment::getExtents)
                    .flatMap(Collection::parallelStream)
                    .collect(Collectors.toList())
            );
        });
    }

    IFormController getController() {
        return controller;
    }

    @Override
    public JComponent getComponent() {
        return this.getSelectorPresentation();
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return ExtentView.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public void showExtents(List<Extent> extents) {
        new LinkedList<>(childrenList()).forEach(this::detach);
        List<Segment> loadedSegments = new ArrayList<>();
        extents.sort((o1, o2) -> Boolean.compare(
                o1.getSegment().problemsGetter().get().isEmpty(),
                o2.getSegment().problemsGetter().get().isEmpty()
        ));
        extents.forEach(extent -> {
            if (!loadedSegments.contains(extent.getSegment())) {
                loadedSegments.add(extent.getSegment());
                attach(new ExtentView(extent));
            }
        });
    }


    static class LoadMap extends EntityCommand<CellView> {

        public LoadMap() {
            super(
                    "update map",
                    Language.get(CellView.class, "command@load"),
                    ImageUtils.getByPath("/images/update.png"),
                    Language.get(CellView.class, "command@load"),
                    null
            );
        }

        @Override
        public Kind getKind() {
            return Kind.System;
        }

        @Override
        public void execute(CellView context, Map<String, IComplexType> params) {
            IDataChangeListener listener = new IDataChangeListener() {
                @Override
                public void dataLoaded() {
                    context.getCommand(RunDefragmentation.class).activate();
                    context.controller.getDataProvider().removeDataChangeListener(this);
                }
            };
            context.controller.getDataProvider().addDataChangeListener(listener);
            context.controller.getDataProvider().loadExtents();
        }
    }

    static class RunDefragmentation extends EntityCommand<CellView> {

        public RunDefragmentation() {
            super(
                    "run defragmentation",
                    Language.get(CellView.class, "command@run"),
                    Defragmentation.ICON_RUN,
                    Language.get(CellView.class, "command@run"),
                    cellView -> !cellView.controller.getDataProvider().getExtents().isEmpty()
            );
        }

        @Override
        public Kind getKind() {
            return Kind.System;
        }

        @Override
        public void execute(CellView context, Map<String, IComplexType> params) {
            ITask task = new DefragmentationTask(context.controller, null);
            ServiceRegistry.getInstance()
                    .lookupService(ITaskExecutorService.class)
                    .quietTask(task);
        }
    }
}
