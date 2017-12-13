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
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.ArrStr;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * Поставщик данных на основе исполненного запроса к БД. Результат запроса выводится
 * с диалоге выбора, при этом в качестве результата будет возращено значение
 * первой колонки выбранной строки. Для удобства возможен поиск по всем колонкам.
 */
public class RowSelector implements IDataSupplier<String> {
    
    public enum Mode {
        Value, 
        Row 
    }
    
    private Mode    mode = Mode.Value;
    private JTable  table;
    private Dialog  dialog;
    private String  data;
    private IEditor lookupEditor;
    private TableRowSorter<TableModel> sorter;
    private final DialogButton btnConfirm, btnCancel;
    
    /**
     * Конструктор поставщика.
     * @param mode Режим возврата значения.
     * @param connectionID Поставщик идентификатора подключения.
     * @param query Запрос, при необходимости включающий в себя параметры.
     * @param params Список значений параметров запроса, не указывать если 
     * параметров нет.
     */
    public RowSelector(Mode mode, Supplier<Integer> connectionID, String query, Object... parameters) {
        this(connectionID, query, parameters);
        this.mode = mode;
    }
    
    /**
     * Конструктор поставщика.
     * @param connectionID Поставщик идентификатора подключения.
     * @param query Запрос, при необходимости включающий в себя параметры.
     * @param params Список значений параметров запроса, не указывать если 
     * параметров нет.
     */
    public RowSelector(Supplier<Integer> connectionID, String query, Object... parameters) {
        btnConfirm = Dialog.Default.BTN_OK.newInstance();
        btnCancel  = Dialog.Default.BTN_CANCEL.newInstance();
        btnConfirm.setEnabled(false);
        
        dialog = new Dialog(
                FocusManager.getCurrentManager().getActiveWindow(),
                ImageUtils.getByPath("/images/selector.png"), 
                Language.get("title"),
                new JPanel(),
                (event) -> {
                    if (event.getID() == Dialog.OK) {
                        if (table.getSelectedRow() != TableModelEvent.HEADER_ROW) {
                            if (mode == Mode.Value) {
                                data = (String) table.getValueAt(table.getSelectedRow(), 0);
                            } else {
                                List<String> values = new LinkedList();
                                for (int column = 0; column < table.getColumnCount(); column++) {
                                    values.add((String) table.getValueAt(table.getSelectedRow(), column));
                                }
                                data = ArrStr.merge(values);
                            }
                        }
                    }
                },
                btnConfirm, btnCancel
        ) {{
            // Перекрытие обработчика кнопок
            Function<DialogButton, ActionListener> defaultHandler = handler;
            handler = (button) -> {
                return (event) -> {
                    if (event.getID() != Dialog.OK || !lookupEditor.getFocusTarget().isFocusOwner()) {
                        defaultHandler.apply(button).actionPerformed(event);
                    }
                }; 
            };
        }
            @Override
            public void setVisible(boolean visible) {
                JPanel content = new JPanel(new BorderLayout());
                IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
                try {
                    Integer connID = connectionID.get();
                    if (connID != null) {
                        ResultSet rset = DAS.select(connID, query, parameters);
                        ResultSetMetaData meta = rset.getMetaData();
                        int colomnCount = meta.getColumnCount();
                        Vector<String> columns = new Vector<>();
                        for (int colIdx = 1; colIdx <= colomnCount; colIdx++) {
                            columns.add(meta.getColumnName(colIdx));
                        }

                        DefaultTableModel tableModel = new DefaultTableModel(null, columns) {
                            @Override
                            public Class<?> getColumnClass(int columnIndex) {
                                return String.class;
                            }

                            @Override
                            public boolean isCellEditable(int row, int column) {
                                return false;
                            }
                        };
                        table = new SelectorTable(tableModel);
                        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                        table.setDefaultRenderer(String.class, new GeneralRenderer());
                        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
                        table.getSelectionModel().addListSelectionListener((ListSelectionEvent event) -> {
                            if (event.getValueIsAdjusting()) return;
                            btnConfirm.setEnabled(true);
                        });
                        table.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent event) {
                                if (event.getClickCount() == 2) {
                                    btnConfirm.click();
                                }
                            }
                        });

                        sorter = new TableRowSorter<>(table.getModel());
                        table.setRowSorter(sorter);

                        PropertyHolder lookupHolder = new PropertyHolder(
                                "filter", null, 
                                Language.get(RowSelector.class.getSimpleName(), "filter"), 
                                new Str(null), false
                        );
                        lookupEditor = new StrEditor(lookupHolder);
                        EditorCommand search = new ApplyFilter();
                        lookupEditor.addCommand(search);
                        lookupEditor.getEditor().add((JComponent) search.getButton());
                        lookupHolder.addChangeListener((name, oldValue, newValue) -> {
                            search.execute(lookupHolder);
                        });

                        JLabel filterIcon  = new JLabel(ImageUtils.resize(ImageUtils.getByPath("/images/filter.png"), 20, 20));
                        filterIcon.setBorder(new EmptyBorder(0, 5, 0, 5));

                        JPanel filterPanel = new JPanel(new BorderLayout());
                        filterPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
                        filterPanel.add(filterIcon, BorderLayout.WEST);
                        filterPanel.add(lookupEditor.getEditor(), BorderLayout.CENTER);

                        content.add(filterPanel, BorderLayout.NORTH);

                        final JScrollPane scrollPane = new JScrollPane();
                        scrollPane.getViewport().setBackground(Color.WHITE);
                        scrollPane.setViewportView(table);
                        scrollPane.setBorder(new CompoundBorder(
                                new EmptyBorder(5, 5, 5, 5), 
                                new MatteBorder(1, 1, 1, 1, Color.GRAY)
                        ));
                        content.add(scrollPane, BorderLayout.CENTER);
                        int rowCount = 0;
                        while (rset.next()) {
                            rowCount++;
                            Vector<String> row = new Vector<>();
                            for (int colIdx = 1; colIdx <= colomnCount; colIdx++) {
                                row.add(rset.getString(colIdx));
                            }
                            tableModel.addRow(row);
                        }
                        if (table.getRowSorter().getViewRowCount() == 1) {
                            table.getSelectionModel().setSelectionInterval(0, 0);
                        }
                        
                        dialog.setContent(content);
                        dialog.setMinimumSize(new Dimension(
                                Math.max(table.getColumnCount() * 100, 300),
                                200
                        ));
                        dialog.setPreferredSize(new Dimension(
                                Math.max(table.getColumnCount() * 200, 300),
                                rowCount < 10 ? 300 : 400
                        ));
                        super.setVisible(visible);
                    }
                } catch (SQLException e) {
                    String command = MessageFormat.format(
                            query.replaceAll("\\?", "{0}"),
                            parameters
                    );
                    Logger.getLogger().error(e.getMessage()+"\nQuery: "+command);
                    MessageBox.show(MessageType.ERROR, e.getMessage());
                }
            }
        };
    }

    @Override
    public String call() throws Exception {
        dialog.setVisible(true);
        return data;
    }
    
    private final class HeaderRenderer extends JLabel implements TableCellRenderer {

        public HeaderRenderer() {
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
            if (mode == Mode.Value) {
                setIcon(column == 0 ? 
                        ImageUtils.resize(ImageUtils.getByPath("/images/target.png"), 18, 18) : 
                        null
                );
            }
            setBorder(new CompoundBorder(
                    new MatteBorder(0, column == 0 ? 0 : 1, 1, 0, Color.GRAY),
                    new EmptyBorder(1, 6, 0, 0)
            ));
            return this;
        }
    }
    
    private class ApplyFilter extends EditorCommand {

        public ApplyFilter() {
            super(ImageUtils.resize(ImageUtils.getByPath("/images/search.png"), 18, 18), null);
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
            }
            if (table.getRowSorter().getViewRowCount() == 1) {
                table.getSelectionModel().setSelectionInterval(0, 0);
            }
        }
    
    }
    
}
