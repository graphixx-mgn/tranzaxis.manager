package codex.presentation;

import codex.log.Logger;
import java.awt.Cursor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DragSource;
import java.io.IOException;
import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

/**
 * Обработчик перемещения (drad and drop) строк таблицы презентации селектора.
 */
public abstract class TableTransferHandler extends TransferHandler {

    private final DataFlavor localObjectFlavor = new ActivationDataFlavor(
            Integer.class, 
            "application/x-java-Integer;class=java.lang.Integer", 
            "Integer Row Index"
    );
    private JTable table = null;

    /**
     * Конструктор обработчика.
     * @param table Ссылка на таблицу.
     */
    TableTransferHandler(JTable table) {
        this.table = table;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        return new DataHandler(table.getSelectedRow(), localObjectFlavor.getMimeType());
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
        boolean b = info.getComponent() == table && info.isDrop() && info.isDataFlavorSupported(localObjectFlavor) && table.getSelectedRowCount() == 1;
        table.setCursor(b ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
        return b;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY_OR_MOVE;
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport info) {
        JTable target = (JTable) info.getComponent();
        JTable.DropLocation dl = (JTable.DropLocation) info.getDropLocation();
        int index = dl.getRow();
        int max = table.getModel().getRowCount();
        if (index < 0 || index > max) {
            index = max;
        }
        target.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        try {
            int rowFrom = (Integer) info.getTransferable().getTransferData(localObjectFlavor);
            if (rowFrom != -1 && rowFrom != index) {
                if (index > rowFrom)
                    index--;
                moveData(rowFrom, index);
                table.getSelectionModel().setSelectionInterval(index, index);
                return true;
            }
        } catch (UnsupportedFlavorException | IOException e) {
            Logger.getLogger().error("Unknown error during moving row", e);
        }
        return false;
    }

    abstract void moveData(int from, int to);

    @Override
    protected void exportDone(JComponent c, Transferable t, int act) {
        if ((act == TransferHandler.MOVE) || (act == TransferHandler.NONE)) {
            table.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }
}