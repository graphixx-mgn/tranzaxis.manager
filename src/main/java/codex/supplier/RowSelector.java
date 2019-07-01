package codex.supplier;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.editor.StrEditor;
import codex.log.Logger;
import codex.presentation.SelectorTable;
import codex.property.PropertyHolder;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class RowSelector<R> extends DataSelector<Map<String, String>, R> implements AdjustmentListener {

    private static final ImageIcon ICON_FILTER   = ImageUtils.resize(ImageUtils.getByPath("/images/filter.png"), 20, 20);
    private static final ImageIcon ICON_SELECTOR = ImageUtils.getByPath("/images/selector.png");
    private static final ImageIcon ICON_SEARCH   = ImageUtils.getByPath("/images/search.png");
    private static final ImageIcon ICON_REVERT   = ImageUtils.getByPath("/images/undo.png");
    private static final ImageIcon ICON_TARGET   = ImageUtils.resize(ImageUtils.getByPath("/images/target.png"), 18, 18);
    private static final ImageIcon ICON_CONFIRM  = ImageUtils.combine(ImageUtils.getByPath("/images/database.png"), ICON_SEARCH);

    private enum Mode {
        Multiple, Single
    }

    public static class Multiple {
        public static RowSelector<List<String>> newInstance(IDataSupplier<Map<String, String>> supplier) {
            return newInstance(supplier, false);
        }

        public static RowSelector<List<String>> newInstance(IDataSupplier<Map<String, String>> supplier, boolean showDesc) {
            return new RowSelector<List<String>>(Mode.Multiple, supplier, showDesc) {
                @Override
                protected Map<String, String> valueToMap(List<String> initial) {
                    return initial == null || initial.isEmpty() ?
                            Collections.emptyMap() :
                            new LinkedHashMap<String, String>() {{
                                for (int index = 0; index < initial.size(); index++) {
                                    put(String.valueOf(index), initial.get(index));
                                }
                            }};
                }

                @Override
                protected List<String> getResult() {
                    int row = this.table.getSelectedRow();
                    if (row != TableModelEvent.HEADER_ROW) {
                        List<String> result = new LinkedList<>();
                        for (int column = 0; column < table.getColumnCount(); column++) {
                            result.add(table.getValueAt(row, column).toString());
                        }
                        return result;
                    } else {
                        return null;
                    }
                }
            };
        }
    }


    public static class Single {
        public static RowSelector<String> newInstance(IDataSupplier<Map<String, String>> supplier) {
            return newInstance(supplier, false);
        }

        public static RowSelector<String> newInstance(IDataSupplier<Map<String, String>> supplier, boolean showDesc) {
            return new RowSelector<String>(Mode.Single, supplier, showDesc) {
                @Override
                protected Map<String, String> valueToMap(String initial) {
                    return initial == null || initial.isEmpty() ?
                           Collections.emptyMap() :
                           Collections.singletonMap("", initial);
                }

                @Override
                protected String getResult() {
                    int row = this.table.getSelectedRow();
                    if (row != TableModelEvent.HEADER_ROW) {
                        return table.getValueAt(row, 0).toString();
                    } else {
                        return null;
                    }
                }
            };
        }
    }


    private DialogButton btnConfirm = Dialog.Default.BTN_OK.newInstance();
    private DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

    private final JTextPane descPane = new JTextPane(){{
        setBorder(new CompoundBorder(
                new EmptyBorder(0, 5, 5, 5),
                new CompoundBorder(
                        new MatteBorder(1, 1, 1, 1, Color.GRAY),
                        new EmptyBorder(3, 3, 3, 3)
                )
        ));
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        setContentType("text/html");
        setEditable(false);
        setVisible(false);
        setOpaque(false);
    }};

    protected final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    protected final JTable table = new SelectorTable(tableModel);
    private final TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
    private final JScrollPane scrollPane = new JScrollPane(table) {{
        getViewport().setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));
    }};

    private final Mode mode;
    private int initialSelectedRow = TableModelEvent.HEADER_ROW;

    private RowSelector(Mode mode, IDataSupplier<Map<String, String>> supplier, boolean showDescription) {
        super(supplier);
        this.mode = mode;
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(String.class, new GeneralRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (row == initialSelectedRow) {
                    c.setFont(IEditor.FONT_VALUE.deriveFont(Font.BOLD));
                    c.setForeground(Color.decode("#0066CC"));
                }
                return c;
            }
        });
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.setRowSorter(sorter);

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
            if (event.getValueIsAdjusting()) return;
            btnConfirm.setEnabled(
                    table.getRowSorter().getViewRowCount() > 0 &&
                            table.getSelectedRow() > TableModelEvent.HEADER_ROW
            );

            if (showDescription && table.getSelectedRow() > TableModelEvent.HEADER_ROW) {
                int selectedRow = table.getSelectedRow();
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int column = 0; column < tableModel.getColumnCount(); column++) {
                    rowData.put(
                            tableModel.getColumnName(column),
                            tableModel.getValueAt(selectedRow, column).toString()
                    );
                }
                if (!descPane.isVisible()) {
                    descPane.setVisible(true);
                }
                descPane.setText(MessageFormat.format(
                        "<html><table>{0}</table></html>",
                        rowData.entrySet().stream()
                                .map(entry -> MessageFormat.format(
                                        "<tr><td><b>{0}:</b></td><td>{1}</td></tr>",
                                        entry.getKey(),
                                        entry.getValue()
                                ))
                                .collect(Collectors.joining())
                ));
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    btnConfirm.click();
                }
            }
        });

        scrollPane.getVerticalScrollBar().addAdjustmentListener(this);
    }

    private void readNextPage() throws IDataSupplier.LoadDataException {
        List<Map<String, String>> data = getSupplier().getNext();
        if (!data.isEmpty()) {
            if (tableModel.getColumnCount() == 0) {
                data.get(0).keySet().forEach(tableModel::addColumn);
            }
            data.forEach(rowMap -> tableModel.addRow(rowMap.values().toArray()));
        }
    }

    private void readPrevPage() throws IDataSupplier.LoadDataException {
        List<Map<String, String>> data = getSupplier().getPrev();
        if (!data.isEmpty()) {
            if (tableModel.getColumnCount() == 0) {
                data.get(0).keySet().forEach(tableModel::addColumn);
            }

            if (initialSelectedRow != TableModelEvent.HEADER_ROW) {
                initialSelectedRow += data.size();
            }
            data.forEach(rowMap -> tableModel.insertRow(0, rowMap.values().toArray()));
        }
    }

    protected abstract Map<String, String> valueToMap(R initial);
    protected abstract R getResult();

    @Override
    public R select(R initialVal) {
        Window window = FocusManager.getCurrentManager().getActiveWindow();
        window.setCursor(new Cursor(Cursor.WAIT_CURSOR));

        getSupplier().reset();
        sorter.setRowFilter(null);
        tableModel.getDataVector().removeAllElements();
        tableModel.fireTableDataChanged();

        if (getSupplier().ready()) {
            btnConfirm.setEnabled(false);

            JPanel filterPanel = new JPanel(new BorderLayout());
            PropertyHolder<Str, String> lookupHolder = new PropertyHolder<>(
                    "filter", null, null,
                    new Str(null), false
            );
            StrEditor lookupEditor = new StrEditor(lookupHolder);

            ApplyFilter search = new ApplyFilter();
            lookupEditor.addCommand(search);
            lookupHolder.addChangeListener((name, oldValue, newValue) -> {
                if (lookupEditor.getFocusTarget().isFocusOwner()) {
                    search.execute(lookupHolder);
                }
            });

            filterPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
            filterPanel.add(lookupEditor.getEditor(), BorderLayout.CENTER);

            JLabel filterIcon  = new JLabel(ICON_FILTER);
            filterIcon.setBorder(new EmptyBorder(0, 5, 0, 5));
            filterPanel.add(filterIcon, BorderLayout.WEST);

            JPanel content = new JPanel(new BorderLayout());
            content.add(filterPanel, BorderLayout.NORTH);
            content.add(scrollPane, BorderLayout.CENTER);
            content.add(descPane, BorderLayout.SOUTH);

            new Dialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    ICON_SELECTOR,
                    Language.get(RowSelector.class, "title"),
                    content,
                    (event) -> {
                        if (event.getID() != Dialog.OK) {
                            table.clearSelection();
                        }
                    },
                    btnConfirm, btnCancel
            ) {
                {
                    // Перекрытие обработчика кнопок
                    Function<DialogButton, ActionListener> defaultHandler = handler;
                    handler = (button) -> (event) -> {
                        if (event.getID() != Dialog.OK || !lookupEditor.getFocusTarget().isFocusOwner()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    };
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(Math.max(table.getColumnCount() * 200, 300), 500);
                }

                @Override
                public void setVisible(boolean visible) {
                    if (visible) {
                        try {
                            readNextPage();
                            window.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

                            if (initialVal != null) {
                                Map<String, String> initialValues = valueToMap(initialVal);
                                if (!initialValues.isEmpty()) {
                                    for (int row = 0; row < tableModel.getRowCount(); row++) {
                                        if (getRowValues(row).containsAll(initialValues.values())) {
                                            initialSelectedRow = row;
                                            table.scrollRectToVisible(new Rectangle(table.getCellRect(initialSelectedRow, 0, true)));
                                            table.getSelectionModel().setSelectionInterval(initialSelectedRow, initialSelectedRow);

                                            BackToInitial back = new BackToInitial();
                                            lookupEditor.addCommand(back);
                                            break;
                                        }
                                    }
                                }
                            }
                            super.setVisible(true);
                        } catch (IDataSupplier.LoadDataException e) {
                            Logger.getLogger().warn("Provider data request failed: {0}", e.getMessage());
                            MessageBox.show(MessageType.ERROR, e.getMessage());
                        }
                    } else {
                        super.setVisible(false);
                    }
                }
            }.setVisible(true);
        }
        return getResult();
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent event) {
        if (!event.getValueIsAdjusting()) {
            JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
            int extent = scrollBar.getModel().getExtent();
            int maximum = scrollBar.getModel().getMaximum();
            int minimum = scrollBar.getModel().getMinimum();

            if (extent + event.getValue() == maximum && getSupplier().available(IDataSupplier.ReadDirection.Forward)) {
                try {
                    table.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    readNextPage();
                    table.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                } catch (IDataSupplier.LoadDataException e) {
                    e.printStackTrace();
                }
            }
            if (event.getValue() == minimum && getSupplier().available(IDataSupplier.ReadDirection.Backward)) {
                try {
                    table.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                    BoundedRangeModel scrollModel = scrollPane.getVerticalScrollBar().getModel();
                    int prevMaximum = scrollModel.getMaximum();
                    scrollPane.getVerticalScrollBar().removeAdjustmentListener(this);

                    readPrevPage();

                    SwingUtilities.invokeLater(() -> {
                        int currMaximum = scrollModel.getMaximum();
                        scrollPane.getVerticalScrollBar().setValue(currMaximum - prevMaximum);
                        scrollPane.getVerticalScrollBar().addAdjustmentListener(this);
                    });
                    table.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                } catch (IDataSupplier.LoadDataException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private List<String> getRowValues(int row) {
        List<String> rowValues = new LinkedList<>();
        for (int col = 0; col < tableModel.getColumnCount(); col++) {
            rowValues.add(tableModel.getValueAt(row, col).toString());
        }
        return rowValues;
    }


    private class BackToInitial extends EditorCommand<Str, String> {

        public BackToInitial() {
            super(
                    ImageUtils.resize(ICON_REVERT, 18, 18),
                    null,
                    holder -> initialSelectedRow != TableModelEvent.HEADER_ROW
            );
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            table.getSelectionModel().setSelectionInterval(initialSelectedRow, initialSelectedRow);
            table.scrollRectToVisible(new Rectangle(table.getCellRect(table.getSelectedRow(), 0, true)));
        }
    }


    private class ApplyFilter extends EditorCommand<Str, String> {

        ApplyFilter() {
            super(ImageUtils.resize(ICON_SEARCH, 18, 18), null);
        }

        private void applyFilter(String lookupValue) {
            RowFilter<TableModel, Object> filter = RowFilter.regexFilter(
                    Pattern.compile(lookupValue, Pattern.CASE_INSENSITIVE).toString()
            );
            scrollPane.getVerticalScrollBar().removeAdjustmentListener(RowSelector.this);
            RowSelector.this.sorter.setRowFilter(filter);
        }

        private boolean dataAvailable() {
            return getSupplier().available(IDataSupplier.ReadDirection.Forward) ||
                   getSupplier().available(IDataSupplier.ReadDirection.Backward);
        }

        private boolean checkAndConfirm() {
            return
                    table.getRowSorter().getViewRowCount() == 0 &&
                    dataAvailable() &&
                    MessageBox.confirmation(
                            ICON_CONFIRM,
                            Language.get(RowSelector.class, "confirm@title"),
                            MessageFormat.format(Language.get(RowSelector.class, "confirm@text"), tableModel.getRowCount())
                    );
        }

        private void resetFilter() {
            RowSelector.this.sorter.setRowFilter(null);
            if (table.getSelectedRow() != TableModelEvent.HEADER_ROW) {
                SwingUtilities.invokeLater(() -> {
                    table.scrollRectToVisible(new Rectangle(table.getCellRect(table.getSelectedRow(), 0, true)));
                    scrollPane.getVerticalScrollBar().addAdjustmentListener(RowSelector.this);
                });
            }
        }

        private IDataSupplier.ReadDirection getStartDirection() {
            return getSupplier().available(IDataSupplier.ReadDirection.Forward) ?
                    IDataSupplier.ReadDirection.Forward :
                    IDataSupplier.ReadDirection.Forward;
        }

        private IDataSupplier.ReadDirection getNextDirection(IDataSupplier.ReadDirection prevDirection) {
            IDataSupplier.ReadDirection nextDirection = prevDirection.equals(IDataSupplier.ReadDirection.Forward) ?
                    IDataSupplier.ReadDirection.Backward :
                    IDataSupplier.ReadDirection.Forward;
            return getSupplier().available(nextDirection) ? nextDirection : prevDirection;
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            String lookupValue = context.getPropValue().getValue();

            if (lookupValue == null || lookupValue.isEmpty()) {
                resetFilter();
            } else {
                applyFilter(lookupValue);
                if (table.getRowSorter().getViewRowCount() == 0) {
                    if (checkAndConfirm()) {
                        new Thread(() -> {
                            MessageBox.ProgressDialog progress = MessageBox.progressDialog(
                                    ICON_SEARCH,
                                    Language.get(RowSelector.class, "wait@title")
                            );

                            IDataSupplier.ReadDirection searchDirection = null;
                            do {
                                if (searchDirection == null) {
                                    searchDirection = getStartDirection();
                                } else {
                                    searchDirection = getNextDirection(searchDirection);
                                }
                                if (!progress.isVisible() && !progress.isCanceled()) {
                                    progress.setVisible(true);
                                } else if (progress.isCanceled()) {
                                    break;
                                }
                                try {
                                    switch (searchDirection) {
                                        case Forward:
                                            readNextPage();
                                            break;
                                        case Backward:
                                            readPrevPage();
                                            break;
                                    }
                                    progress.setDescription(MessageFormat.format(
                                            Language.get(RowSelector.class, "wait@progress"),
                                            tableModel.getRowCount()
                                    ));
                                } catch (IDataSupplier.LoadDataException e) {
                                    MessageBox.show(MessageType.ERROR, e.getMessage());
                                    break;
                                }
                            } while (table.getRowSorter().getViewRowCount() == 0 && dataAvailable());

                            progress.setVisible(false);
                            if (table.getRowSorter().getViewRowCount() == 0) {
                                if (!progress.isCanceled()) {
                                    MessageBox.show(MessageType.WARNING, Language.get(RowSelector.class, "warn@notfound"));
                                }
                            } else if (table.getRowSorter().getViewRowCount() == 1) {
                                table.getSelectionModel().setSelectionInterval(0, 0);
                            }
                            resetFilter();
                        }).start();

                    } else {
                        resetFilter();
                    }

                } else {
                    if (table.getRowSorter().getViewRowCount() == 1) {
                        table.getSelectionModel().setSelectionInterval(0, 0);
                    }
                    resetFilter();
                }
            }
        }
    }


    private final class HeaderRenderer extends JLabel implements TableCellRenderer {

        HeaderRenderer() {
            super();
            setOpaque(true);
            setFont(IEditor.FONT_BOLD);
            setForeground(IEditor.COLOR_NORMAL);
            setBackground(Color.decode("#CCCCCC"));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText((String) value);
            if (mode == Mode.Single) {
                setIcon(column == 0 ? ICON_TARGET : null);
            }
            setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 1, column == table.getColumnCount()-1 ? 0 : 1, Color.GRAY),
                    new EmptyBorder(1, 6, 0, 0)
            ));
            return this;
        }
    }

}
