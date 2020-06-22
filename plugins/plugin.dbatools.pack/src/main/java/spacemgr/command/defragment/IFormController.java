package spacemgr.command.defragment;

import codex.task.ITask;
import spacemgr.command.objects.Extent;
import spacemgr.command.objects.IProblematic;
import spacemgr.command.objects.Segment;
import spacemgr.command.objects.TableSpace;
import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

interface IFormController {

    TableSpace    getTableSpace();
    IDataProvider getDataProvider();
    IMapView      getMapView();
    ICellView     getCellView();
    JPanel        getFormView();
    void          initLogOutput(ITask task);


    interface IView {
        JComponent getComponent();
    }

    interface IMapView extends IView {
        int  getBlocksPerCell();

        void insertExtent(Extent extent);
        void removeExtent(Extent extent);
        void showExtents(List<Extent> extents);
    }

    interface ICellView extends IView {
        void showExtents(List<Extent> extents);
    }

    interface IDataProvider {
        default void loadExtents() {}
        default Collection<Extent> getExtents() {
            return Collections.emptyList();
        }
        default Collection<Segment> getSegments() {
            return Collections.emptyList();
        }
        default List<IProblematic> getProblems(Segment segment) {
            return Collections.emptyList();
        }

        default void addDataChangeListener(IDataChangeListener listener) {}
        default void removeDataChangeListener(IDataChangeListener listener) {}
    }
}
