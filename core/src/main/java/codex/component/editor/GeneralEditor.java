package codex.component.editor;

import codex.editor.IEditor;
import codex.model.EntityModel;
import codex.presentation.ISelectorTableModel;
import codex.property.IPropertyChangeListener;
import codex.type.Bool;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

public class GeneralEditor extends AbstractCellEditor implements TableCellEditor {

    private final Map<KeyStroke, Action> KEY_BINDING = new HashMap<>();

    {
        KEY_BINDING.put(
                KeyStroke.getKeyStroke("ESCAPE"),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        cancelCellEditing();
                    }
                }
        );
        KEY_BINDING.put(
                KeyStroke.getKeyStroke("ENTER"),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        stopCellEditing();
                    }
                }
        );
    }

    private EntityModel model;
    private String      propName;
    private Object      prevValue;
    private IPropertyChangeListener applyOnClick = (name, oldValue, newValue) -> stopCellEditing();

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        ISelectorTableModel selectorModel = (ISelectorTableModel) table.getModel();
        model = selectorModel.getEntityForRow(table.convertRowIndexToModel(row)).model;
        propName  = selectorModel.getPropertyForColumn(column);
        prevValue = model.getUnsavedValue(propName);

        EventQueue.invokeLater(() -> model.getEditor(propName).getFocusTarget().requestFocusInWindow());
        attachKeyBinding();

        IEditor editor = model.isPropertyDynamic(propName) ?
                model.getProperty(propName).getPropValue().editorFactory().newInstance(model.getProperty(propName)) :
                model.getEditor(propName);
        if (model.getPropertyType(propName) == Bool.class) {
            JComponent renderedComp = (JComponent) table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, true, true, row, column);
            model.getProperty(propName).addChangeListener(applyOnClick);
            JPanel container = new JPanel();
            container.setOpaque(true);
            container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
            container.setBackground(renderedComp.getBackground());
            container.setBorder(renderedComp.getBorder());
            container.add(editor.getEditor());
            return container;
        } else {
            return editor.getEditor();
        }
    }

    @Override
    public Object getCellEditorValue() {
        model.getEditor(propName).stopEditing();
        return model.getEditor(propName).getValue();
    }

    public boolean stopCellEditing() {
        boolean stopEditing = model.getEditor(propName).stopEditing();
        if (stopEditing) {
            detachKeyBinding();
            model.getProperty(propName).removeChangeListener(applyOnClick);
        }
        return stopEditing && super.stopCellEditing();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void cancelCellEditing() {
        model.getEditor(propName).stopEditing();
        model.setValue(propName, prevValue);
        model.getEditor(propName).setValue(prevValue);
        stopCellEditing();
        super.cancelCellEditing();
    }

    private void attachKeyBinding() {
        JComponent editorComponent =  model.getEditor(propName).getEditor();
        for (KeyStroke key : KEY_BINDING.keySet()) {
            editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, key.toString());
            editorComponent.getActionMap().put(key.toString(), KEY_BINDING.get(key));
        }
    }

    private void detachKeyBinding() {
        JComponent editorComponent =  model.getEditor(propName).getEditor();
        for (KeyStroke key : KEY_BINDING.keySet()) {
            editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).remove(key);
            editorComponent.getActionMap().remove(key.toString());
        }
    }
}
