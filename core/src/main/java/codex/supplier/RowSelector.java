package codex.supplier;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.editor.StrEditor;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorTable;
import codex.presentation.TableColumnAdjuster;
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
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class RowSelector<R> extends DataSelector<Map<String, String>, R> {

    private static final ImageIcon ICON_FILTER   = ImageUtils.getByPath("/images/filter.png");
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
                protected List<String> getValue(Supplier<Map<String, String>> dataSupplier) {
                    Map<String, String> data = dataSupplier.get();
                    return data == null || data.isEmpty() ? Collections.emptyList() : new LinkedList<>(data.values());
                }

                @Override
                protected boolean isEmpty(List<String> value) {
                    return value == null || value.isEmpty();
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
                protected String getValue(Supplier<Map<String, String>> dataSupplier) {
                    Map<String, String> data = dataSupplier.get();
                    return data == null || data.isEmpty() ? null : data.values().iterator().next();
                }

                @Override
                protected boolean isEmpty(String value) {
                    return value == null || value.isEmpty();
                }
            };
        }
    }

    private final boolean desc;
    private final Mode mode;
    private final Map<Integer, TableCellRenderer> rendererMap = new HashMap<>();

    private RowSelector(Mode mode, IDataSupplier<Map<String, String>> supplier, boolean showDescription) {
        super(supplier);
        this.mode = mode;
        this.desc = showDescription;
    }

    protected abstract R getValue(Supplier<Map<String, String>> dataSupplier);
    protected abstract boolean isEmpty(R value);

    @Override
    public R select(R initialVal) {
        SelectorDialog dialog = new SelectorDialog(initialVal);
        dialog.setVisible(true);
        return dialog.getExitCode() == Dialog.OK ? getValue(dialog::getData) : null;
    }

    private List<Map<String, String>> readData(IDataSupplier.ReadDirection direction) {
        try {
            return direction.equals(IDataSupplier.ReadDirection.Forward) ? getSupplier().getNext() : getSupplier().getPrev();
        } catch (IDataSupplier.LoadDataException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public final void setColumnRenderer(int column, TableCellRenderer renderer) {
        rendererMap.put(column, renderer);
    }


    private class SelectorDialog extends Dialog {

        private R   initialVal;
        private int initialRow = -1;

        private PropertyHolder<Str, String> searchHolder = new PropertyHolder<>(
                "filter", null, null,
                new Str(null), false
        );
        private final DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        private final TableRowSorter<TableModel> sorter = new TableRowSorter<>(tableModel);

        private final JTable    table = new SelectorTable(tableModel);
        private final StrEditor searchEditor = new StrEditor(searchHolder);
        private final JPanel    filterPanel  = new JPanel(new BorderLayout()) {{
            setBorder(new EmptyBorder(5, 5, 0, 5));
            add(new JLabel(ICON_FILTER) {{
                setBorder(new EmptyBorder(0, 5, 0, 5));
            }}, BorderLayout.WEST);
            add(searchEditor.getEditor(), BorderLayout.CENTER);
        }};
        private final JTextPane descPane = new JTextPane() {{
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
        private final JScrollPane scrollPane = new JScrollPane(table) {{
            getViewport().setBackground(Color.WHITE);
            setBorder(new CompoundBorder(
                    new EmptyBorder(5, 5, 5, 5),
                    new MatteBorder(1, 1, 1, 1, Color.GRAY)
            ));
        }};

        private SelectorDialog(R initialVal) {
            super(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    ICON_SELECTOR,
                    Language.get(RowSelector.class, "title"),
                    new JPanel(),
                    e -> {},
                    Dialog.Default.BTN_OK.newInstance(),
                    Dialog.Default.BTN_CANCEL.newInstance()
            );
            this.initialVal = initialVal;
            table.setFillsViewportHeight(true);
            table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
            table.setDefaultRenderer(String.class, new GeneralRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = rendererMap.containsKey(column) ?
                            rendererMap.get(column).getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) :
                            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    int rowIdx = table.convertRowIndexToModel(row);
                    if (rowIdx == initialRow) {
                        c.setFont(IEditor.FONT_VALUE.deriveFont(Font.BOLD));
                        c.setForeground(Color.decode("#0066CC"));
                    }
                    return c;
                }
            });
            table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(this::onSelect);
            table.setRowSorter(sorter);

            TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 6);
            adjuster.setDynamicAdjustment(true);
            adjuster.adjustColumns();

            scrollPane.getVerticalScrollBar().addAdjustmentListener(this::onScroll);
            scrollPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (table.getSelectedRow() != -1) {
                        table.scrollRectToVisible(getRowRect(table.getSelectedRow()));
                    }
                }
            });

            final ApplyFilter search = new ApplyFilter(this::onFilter);
            searchEditor.addCommand(search);
            searchHolder.addChangeListener((name, oldValue, newValue) -> {
                if (searchEditor.getFocusTarget().isFocusOwner()) {
                    search.execute(searchHolder);
                }
            });
            getButton(Dialog.OK).setEnabled(false);
        }

        {
            getSupplier().reset();
            setContent(new JPanel(new BorderLayout()) {{
                add(filterPanel, BorderLayout.NORTH);
                add(scrollPane,  BorderLayout.CENTER);
                add(descPane,    BorderLayout.SOUTH);
            }});
            // Перекрытие обработчика кнопок
            Function<DialogButton, ActionListener> defaultHandler = handler;
            handler = (button) -> (event) -> {
                if (event.getID() != Dialog.OK || !searchEditor.getFocusTarget().isFocusOwner()) {
                    defaultHandler.apply(button).actionPerformed(event);
                }
            };
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension defDim = super.getPreferredSize();
            int width = defDim.width;
            if (getOwner() != null && getOwner() instanceof Dialog) {
                if (width > EditorPresentation.DEFAULT_DIALOG_WIDTH / 2 && width < EditorPresentation.DEFAULT_DIALOG_WIDTH) {
                    width = getOwner().getPreferredSize().width - 20;
                }
            }
            return new Dimension(
                    width + 40,
                    Math.max(Math.min(defDim.height, EditorPresentation.DEFAULT_DIALOG_HEIGHT), 200)  // 200 <= height <= 500
            );
        }

        Rectangle getRowRect(int row) {
            Rectangle cellRect = table.getCellRect(row, 0, true);
            cellRect.setBounds(0, cellRect.y-table.getRowHeight(), 0, cellRect.height+table.getRowHeight()*2);
            return cellRect;
        }

        private void readPage(IDataSupplier.ReadDirection direction) {
            List<Map<String, String>> data = readData(direction);
            if (data.isEmpty()) return;

            final Runnable runnable = () -> {
                if (tableModel.getColumnCount() == 0) {
                    data.get(0).keySet().forEach(tableModel::addColumn);
                }
                TableModelListener[] listeners = tableModel.getTableModelListeners();
                for (TableModelListener listener : listeners) {
                    tableModel.removeTableModelListener(listener);
                }
                int minIdx = tableModel.getRowCount();
                int maxIdx = minIdx + data.size() -1;

                data.forEach(rowMap -> {
                    int newRowIdx = direction.equals(IDataSupplier.ReadDirection.Forward) ? tableModel.getRowCount() : 0;
                    tableModel.insertRow(newRowIdx, rowMap.values().toArray());
                    if (initialRow == -1) {
                        if (getValue(() -> rowMap).equals(initialVal)) {
                            initialRow = newRowIdx;
                        }
                    } else if (direction.equals(IDataSupplier.ReadDirection.Backward)) {
                        initialRow++;
                    }
                });
                for (TableModelListener listener : listeners) {
                    tableModel.addTableModelListener(listener);
                }
                SwingUtilities.invokeLater(() -> {
                    synchronized (table.getRowSorter()) {
                        tableModel.fireTableRowsInserted(minIdx, maxIdx);
                        if (initialRow != -1 && table.getSelectedRow() == -1) {
                            table.setRowSelectionInterval(initialRow, initialRow);
                        }
                    }
                });
            };
            runnable.run();
        }

        private Map<String, String> getSelectedData() {
            int selectedRow = table.convertRowIndexToModel(table.getSelectedRow());
            return selectedRow == TableModelEvent.HEADER_ROW ?
                    Collections.emptyMap() :
                    IntStream.range(0, tableModel.getColumnCount()).boxed()
                            .collect(Collectors.toMap(
                                    tableModel::getColumnName,
                                    column -> (String) tableModel.getValueAt(selectedRow, column),
                                    (u, v) -> {
                                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                                    },
                                    LinkedHashMap::new
                            ));
        }

        private void onScroll(AdjustmentEvent event) {
            if (!event.getValueIsAdjusting()) {
                JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
                int extent  = scrollBar.getModel().getExtent();
                int maximum = scrollBar.getModel().getMaximum();
                int minimum = scrollBar.getModel().getMinimum();
                if (extent + event.getValue() == maximum) {
                    if (getSupplier().available(IDataSupplier.ReadDirection.Forward)) {
                        setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        readPage(IDataSupplier.ReadDirection.Forward);
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        if (!isVisible()) pack();
                    }
                }
                if (event.getValue() == minimum) {
                    if (getSupplier().available(IDataSupplier.ReadDirection.Backward)) {
                        setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        readPage(IDataSupplier.ReadDirection.Backward);
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                        if (!isVisible()) pack();
                    }
                }
            }
        }

        private void onSelect(ListSelectionEvent event) {
            if (!event.getValueIsAdjusting() && event.getFirstIndex() != -1) {
                Map<String, String> rowMap = getSelectedData();
                R value = getValue(() -> rowMap);
                getButton(Dialog.OK).setEnabled(!isEmpty(value) && !value.equals(initialVal));

                if (desc && !isEmpty(value)) {
                    descPane.setText(MessageFormat.format(
                            "<html><table>{0}</table></html>",
                            rowMap.entrySet().stream()
                                    .map(entry -> MessageFormat.format(
                                            "<tr><td><b>{0}:</b></td><td>{1}</td></tr>",
                                            entry.getKey(),
                                            entry.getValue()
                                    ))
                                    .collect(Collectors.joining())
                    ));
                    if (!descPane.isVisible()) {
                        descPane.setVisible(true);
                    }
                }
            }
        }

        private void onFilter(String value) {
            final Supplier<Boolean> dataAvailable = () ->
                    getSupplier().available(IDataSupplier.ReadDirection.Forward) ||
                    getSupplier().available(IDataSupplier.ReadDirection.Backward);
            final Function<Boolean, List<Integer>> nextRows = (showAll) -> {
                int selectedRow = table.getSelectedRow();
                try {
                    applyFilter(value);
                    return table.getRowSorter().getViewRowCount() == 0 ?
                            Collections.emptyList() :
                            IntStream.range(0, table.getRowSorter().getViewRowCount())
                                    .boxed()
                                    .map(table::convertRowIndexToModel)
                                    .filter(row -> showAll || row > selectedRow)
                                    .collect(Collectors.toList());
                } finally {
                    resetFilter();
                }
            };
            final MessageBox.ProgressDialog progress = MessageBox.progressDialog(
                    ICON_SEARCH,
                    Language.get(RowSelector.class, "wait@title")
            );

            if (value != null && !value.isEmpty()) {
                new Thread(() -> {
                    boolean confirmed = false;
                    while (true) {
                        List<Integer> foundRows;
                        synchronized (table.getRowSorter()) {
                            foundRows = nextRows.apply(false);
                        }
                        if (foundRows.isEmpty()) {
                            if (dataAvailable.get()) {
                                confirmed = confirmed || MessageBox.confirmation(
                                        ICON_CONFIRM,
                                        Language.get(RowSelector.class, "confirm@title"),
                                        MessageFormat.format(Language.get(RowSelector.class, "confirm@text"), tableModel.getRowCount())
                                );
                                if (confirmed) {
                                    synchronized (progress) {
                                        if (progress.isCanceled()) {
                                            break;
                                        }
                                    }
                                    SwingUtilities.invokeLater(() -> {
                                        synchronized (progress) {
                                            if (!progress.isVisible()) {
                                                progress.setVisible(true);
                                            } else {
                                                progress.setDescription(MessageFormat.format(
                                                        Language.get(RowSelector.class, "wait@progress"),
                                                        tableModel.getRowCount()
                                                ));
                                            }
                                        }
                                    });
                                    readPage(IDataSupplier.ReadDirection.Forward);
                                } else {
                                    break;
                                }
                            } else {
                                synchronized (progress) {
                                    if (progress.isVisible()) {
                                        progress.setVisible(false);
                                    }
                                }
                                List<Integer> allRows = nextRows.apply(true);
                                if (!allRows.isEmpty()) {
                                    table.clearSelection();
                                    continue;
                                } else {
                                    MessageBox.show(MessageType.WARNING, Language.get(RowSelector.class, "warn@notfound"));
                                }
                                break;
                            }
                        } else {
                            int nextRow = foundRows.iterator().next();
                            SwingUtilities.invokeLater(() -> {
                                table.getSelectionModel().setSelectionInterval(nextRow, nextRow);
                                table.scrollRectToVisible(getRowRect(nextRow));
                                synchronized (progress) {
                                    if (progress.isVisible()) {
                                        progress.setVisible(false);
                                    }
                                }
                            });
                            break;
                        }
                    }
                }).start();
            }
        }

        private void applyFilter(String lookupValue) {
            RowFilter<TableModel, Object> filter = RowFilter.regexFilter(
                    Pattern.compile(lookupValue, Pattern.CASE_INSENSITIVE).toString()
            );
            sorter.setRowFilter(filter);
        }

        private void resetFilter() {
            sorter.setRowFilter(null);
        }

        private Map<String, String> getData() {
            return getSelectedData();
        }
    }

    private static class ApplyFilter extends EditorCommand<Str, String> {

        private final Consumer<String> action;

        ApplyFilter(Consumer<String> onExecute) {
            super(ICON_SEARCH, null);
            this.action = onExecute;
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            action.accept(context.getPropValue().getValue());
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
