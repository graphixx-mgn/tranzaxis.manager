package codex.presentation;

import codex.component.editor.GeneralEditor;
import codex.component.render.GeneralRenderer;
import codex.editor.IEditor;
import codex.type.Bool;
import codex.type.IComplexType;
import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Виджет таблицы селекторов (презентации и диалога).
 */
public final class SelectorTable extends JTable implements IEditableTable {

    private final List<String> editableProps = new LinkedList<>();

    public SelectorTable(TableModel model) {
        this(model, true);
    }
    
    public SelectorTable(TableModel model, boolean autoResize) {
        super(model);
        setRowHeight(IEditor.FONT_VALUE.getSize() * 2);
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0));

        setPreferredScrollableViewportSize(new Dimension(getPreferredSize().width, 200));
        setFillsViewportHeight(true);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "none");

        GeneralRenderer renderer = new GeneralRenderer<>();
        setDefaultRenderer(Bool.class, renderer);
        setDefaultRenderer(IComplexType.class, renderer);
        getTableHeader().setDefaultRenderer(renderer);

        setDefaultEditor(Bool.class, new GeneralEditor());
        setDefaultEditor(IComplexType.class, new GeneralEditor());

        if (autoResize) {
            TableColumnAdjuster adjuster = new TableColumnAdjuster(this, 12);
            adjuster.setDynamicAdjustment(true);
            adjuster.adjustColumns();
        }
    }

    @Override
    public void setPropertiesEditable(String... propNames) {
        if (propNames != null) {
            editableProps.addAll(Arrays.asList(propNames));
        }
    }

    @Override
    public void setPropertyEditable(String propName) {
        editableProps.add(propName);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        if (getModel() instanceof ISelectorTableModel) {
            return editableProps.contains(((ISelectorTableModel) getModel()).getPropertyForColumn(column));
        } else {
            return false;
        }
    }
}
