package codex.presentation;

import codex.command.EntityCommand;
import codex.component.render.DefaultRenderer;
import codex.editor.IEditor;
import codex.model.Access;
import codex.model.Entity;
import codex.model.EntityModel;
import codex.model.IModelListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Презентация селектора сущности. Реализует как функциональность отображения и 
 * редактирования дочерних сущностей, так и обеспечивает работу команд по созданию
 * новых сущностей.
 */
public final class SelectorPresentation extends JPanel implements IModelListener {
    
    private final CommandPanel        commandPanel = new CommandPanel();
    private final List<EntityCommand> commands = new LinkedList<>();
    private final DefaultTableModel   tableModel;
    
    /**
     * Конструктор презентации. 
     */
    public SelectorPresentation(Entity entity) {
        super(new BorderLayout());
        if (!entity.model.getProperties(Access.Edit).isEmpty()) {
            setBorder(new CompoundBorder(
                    new EmptyBorder(0, 5, 0, 5), 
                    new LineBorder(Color.GRAY, 1)
            ));
        }
        Entity prototype = Entity.newInstance(entity.getChildClass(), null);
        
        commands.add(new EditEntity());
        commands.add(new CreateEntity(entity.getChildClass()));
        commands.add(new CloneEntity());
        commands.add(new DeleteEntity());
        commandPanel.addCommands(commands.toArray(new EntityCommand[]{}));
        commandPanel.addSeparator();
        
        commands.addAll(prototype.getCommands());
        commandPanel.addCommands(prototype.getCommands().toArray(new EntityCommand[]{}));
        
        commands.forEach((command) -> {
            command.setContext();
        });
        
        add(commandPanel, BorderLayout.NORTH);
        
        Vector dataVector = new Vector();
        entity.childrenList().forEach((node) -> {
            Vector rowVector = new Vector<>();
            Entity child = (Entity) node;
            child.model.getProperties(Access.Select).forEach((String propName) -> {
                rowVector.add(propName.equals(EntityModel.PID) ? child : child.model.getValue(propName));
            });
            dataVector.addElement(rowVector);
            child.model.addModelListener(this);
        });
        
        tableModel = new DefaultTableModel(
                dataVector,
                new Vector<>(
                        prototype.model.getProperties(Access.Select).stream().map((propName) -> {
                            return prototype.model.getPropertyTitle(propName);
                        }).collect(Collectors.toList())
                )
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        final JTable table = new JTable(tableModel);
        table.setRowHeight((int) (IEditor.FONT_VALUE.getSize() * 2));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0,0));
        table.setPreferredScrollableViewportSize(table.getPreferredSize());
        table.setDefaultRenderer(String.class, new DefaultRenderer());
        table.getTableHeader().setDefaultRenderer(new DefaultRenderer());
        
        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(table);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(0, 5, 5, 5), 
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));
        add(scrollPane, BorderLayout.CENTER);
    }
    
    @Override
    public void modelSaved(EntityModel model, List<String> changes) {
        int rowCount = this.tableModel.getRowCount();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++) {
            if (((Entity) this.tableModel.getValueAt(rowIdx, 0)).model.equals(model)) {
                final int entityIdx = rowIdx;
                List<String> selectorProps = model.getProperties(Access.Select);
                selectorProps.forEach((propName) -> {
                    if (changes.contains(propName)) {
                        int propIdx = selectorProps.indexOf(propName);
                        this.tableModel.setValueAt(model.getValue(propName), entityIdx, propIdx);
                    }
                });
                this.tableModel.fireTableRowsInserted(rowIdx, rowIdx);
                break;
            }
        }
    }
    
}
