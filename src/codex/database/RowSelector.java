package codex.database;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.editor.StrEditor;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.supplier.IDataSupplier;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Vector;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
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
    
    private JTable  table;
    private Dialog  dialog;
    private String  data;
    private IEditor lookupEditor;
    private TableRowSorter<TableModel> sorter;
    
    public RowSelector() {
        dialog = new Dialog(
                null,
                ImageUtils.getByPath("/images/selector.png"), 
                Language.get("title"),
                new JPanel(),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        if (event.getID() == Dialog.OK) {
                            if (table.getSelectedRow() != TableModelEvent.HEADER_ROW) {
                                data = (String) table.getValueAt(table.getSelectedRow(), 0);
                            }
                        }
                    }
                },
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        ) {{
            // Перекрытие обработчика кнопок
            Function<DialogButton, AbstractAction> defaultHandler = handler;
            handler = (button) -> {
                return new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        if (event.getID() != Dialog.OK || !lookupEditor.getFocusTarget().isFocusOwner()) {
                            defaultHandler.apply(button).actionPerformed(event);
                        }
                    }
                }; 
            };
        }
            @Override
            public void setVisible(boolean visible) {
                JPanel content = new JPanel(new BorderLayout());
                IDatabaseAccessService DAS = (IDatabaseAccessService) ServiceRegistry.getInstance().lookupService(OracleAccessService.class);
                try {
                    Integer connectionID = DAS.registerConnection(
                            "jdbc:oracle:thin:@//10.7.1.55:1521/TERMINALPAB", 
                            "TX_WIRECARD_TRUNK", 
                            "TX_WIRECARD_TRUNK"
                    );
                    ResultSet rset = DAS.select(connectionID, "SELECT ID, TITLE FROM RDX_INSTANCE");
                    if (connectionID != null) {
                        try {
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
                            table = new JTable(tableModel);
                            table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
                            table.setShowVerticalLines(false);
                            table.setIntercellSpacing(new Dimension(0,0));
                            table.setPreferredScrollableViewportSize(getPreferredSize());

                            table.setDefaultRenderer(String.class, new GeneralRenderer());
                            table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
                            table.getInputMap(
                                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
                            ).put(KeyStroke.getKeyStroke("ENTER"), "none");
            
                            sorter = new TableRowSorter<>(table.getModel());
                            table.setRowSorter(sorter);

                            PropertyHolder lookupHolder = new PropertyHolder("filter", new Str(null), false);
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
                            while (rset.next()) {
                                Vector<String> row = new Vector<>();
                                for (int colIdx = 1; colIdx <= colomnCount; colIdx++) {
                                    row.add(rset.getString(colIdx));
                                }
                                tableModel.addRow(row);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                dialog.setContent(content);
                dialog.setMinimumSize(new Dimension(
                        table.getColumnCount() * 150,
                        200
                ));
                dialog.setPreferredSize(new Dimension(
                        table.getColumnCount() * 200, 
                        400
                ));
                super.setVisible(visible);
            }
        };
    }

    @Override
    public String call() throws Exception {
        dialog.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
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
            setIcon(column == 0 ? 
                    ImageUtils.resize(ImageUtils.getByPath("/images/target.png"), 18, 18) : 
                    null
            );
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
