package codex.database;

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
import codex.supplier.DataSelector;
import codex.supplier.IDataSupplier;
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
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class RowSelector<R> extends DataSelector<Map<String, String>, R> {

    private static final ImageIcon ICON_FILTER   = ImageUtils.resize(ImageUtils.getByPath("/images/filter.png"), 20, 20);
    private static final ImageIcon ICON_SELECTOR = ImageUtils.getByPath("/images/selector.png");
    private static final ImageIcon ICON_SEARCH   = ImageUtils.getByPath("/images/search.png");
    private static final ImageIcon ICON_TARGET   = ImageUtils.resize(ImageUtils.getByPath("/images/target.png"), 18, 18);
    private static final ImageIcon ICON_CONFIRM  = ImageUtils.combine(ImageUtils.getByPath("/images/database.png"), ICON_SEARCH);

    private enum Mode {
        Multiple, Single
    }


    public static class Multiple {
        public static RowSelector<List<String>> newInstance(IDataSupplier<Map<String, String>> supplier) {
            return new RowSelector<List<String>>(Mode.Multiple, supplier) {
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
            return new RowSelector<String>(Mode.Single, supplier) {
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

    DialogButton btnConfirm = Dialog.Default.BTN_OK.newInstance();
    DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

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
    protected final JTable table = new SelectorTable(tableModel);
    private final TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
    private final JScrollPane scrollPane = new JScrollPane(table) {{
        getViewport().setBackground(Color.WHITE);
        setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));
        getVerticalScrollBar().addAdjustmentListener(event -> {
            if (!event.getValueIsAdjusting() && getSupplier().available()) {
                JScrollBar scrollBar = (JScrollBar) event.getAdjustable();
                int extent = scrollBar.getModel().getExtent();
                int maximum = scrollBar.getModel().getMaximum();
                if (extent + event.getValue() == maximum) {
                    try {
                        table.setCursor(new Cursor(Cursor.WAIT_CURSOR));
                        readPage();
                        table.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    } catch (IDataSupplier.NoDataAvailable e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }};

    private final Mode mode;

    private RowSelector(Mode mode, IDataSupplier<Map<String, String>> supplier) {
        super(supplier);
        this.mode = mode;
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(String.class, new GeneralRenderer());
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.setRowSorter(sorter);

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
            if (event.getValueIsAdjusting()) return;
            btnConfirm.setEnabled(
                    table.getRowSorter().getViewRowCount() > 0 &&
                            table.getSelectedRow() > -1
            );
        });
    }

    private void readPage() throws IDataSupplier.NoDataAvailable {
        List<Map<String, String>> data = getSupplier().get();
        if (!data.isEmpty()) {
            if (tableModel.getColumnCount() == 0) {
                data.get(0).keySet().forEach(tableModel::addColumn);
            }
            data.forEach(rowMap -> tableModel.addRow(rowMap.values().toArray()));
        }
    }

    protected abstract R getResult();

    @Override
    public R select() {
        tableModel.getDataVector().removeAllElements();
        tableModel.fireTableDataChanged();

        if (getSupplier().ready()) {
            btnConfirm.setEnabled(false);

            JPanel filterPanel = new JPanel(new BorderLayout());
            PropertyHolder lookupHolder = new PropertyHolder<>(
                    "filter", null, null,
                    new Str(null), false
            );
            IEditor lookupEditor = new StrEditor(lookupHolder);
            EditorCommand search = new ApplyFilter();
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

            new Dialog(
                    FocusManager.getCurrentManager().getActiveWindow(),
                    ICON_SELECTOR,
                    Language.get(RowSelector.class, "title"),
                    content,
                    (event) -> {
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
                public void setVisible(boolean visible) {
                    if (visible) {
                        try {
                            readPage();
                            setPreferredSize(new Dimension(
                                    Math.max(table.getColumnCount() * 200, 300), 300
                            ));
                            super.setVisible(visible);
                        } catch (IDataSupplier.NoDataAvailable e) {
                            Logger.getLogger().warn("Database query failed: {0}", e.getMessage());
                            MessageBox.show(MessageType.ERROR, e.getMessage());
                        }
                    } else {
                        super.setVisible(visible);
                    }
                }
            }.setVisible(true);
        }
        return getResult();
    }


    private class ApplyFilter extends EditorCommand {

        ApplyFilter() {
            super(ImageUtils.resize(ICON_SEARCH, 18, 18), null);
        }

        @Override
        public void execute(PropertyHolder context) {
            String lookupValue = (String) context.getPropValue().getValue();
            if (lookupValue == null || lookupValue.isEmpty()) {
                RowSelector.this.sorter.setRowFilter(null);
            } else {
                RowFilter<TableModel, Object> filter = RowFilter.regexFilter(
                        Pattern.compile(lookupValue, Pattern.CASE_INSENSITIVE).toString()
                );
                RowSelector.this.sorter.setRowFilter(filter);

                if (table.getRowSorter().getViewRowCount() == 0 && getSupplier().available()) {
                    boolean confirmed = MessageBox.confirmation(
                            ICON_CONFIRM,
                            Language.get(RowSelector.class, "confirm@title"),
                            MessageFormat.format(Language.get(RowSelector.class, "confirm@text"), tableModel.getRowCount())
                    );
                    if (confirmed) {
                        new Thread(() -> {
                            MessageBox.ProgressDialog progress = MessageBox.progressDialog(
                                    ICON_SEARCH,
                                    Language.get(RowSelector.class, "wait@title")
                            );
                            while (table.getRowSorter().getViewRowCount() == 0 && getSupplier().available()) {
                                try {
                                    if (!progress.isVisible() && !progress.isCanceled()) {
                                        progress.setVisible(true);
                                    } else if (progress.isCanceled()) {
                                        break;
                                    }
                                    readPage();
                                    progress.setDescription(
                                            MessageFormat.format(Language.get(RowSelector.class, "wait@progress"), tableModel.getRowCount())
                                    );
                                } catch (IDataSupplier.NoDataAvailable e) {
                                    e.printStackTrace();
                                }
                            }
                            progress.setVisible(false);

                            if (table.getRowSorter().getViewRowCount() == 0) {
                                if (!progress.isCanceled()) {
                                    MessageBox.show(MessageType.WARNING, Language.get(RowSelector.class, "warn@notfound"));
                                }
                                RowSelector.this.sorter.setRowFilter(null);
                            } else if (table.getRowSorter().getViewRowCount() == 1) {
                                table.getSelectionModel().setSelectionInterval(0, 0);
                            }
                        }).start();
                    } else {
                        RowSelector.this.sorter.setRowFilter(null);
                    }
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
