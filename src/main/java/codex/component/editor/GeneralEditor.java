package codex.component.editor;

import codex.model.Access;
import codex.model.EntityModel;
import codex.presentation.SelectorTableModel;
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

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        model = ((SelectorTableModel) table.getModel()).getEntityAt(row).model;
        propName  = model.getProperties(Access.Select).get(column);
        prevValue = model.getUnsavedValue(propName);

        EventQueue.invokeLater(() -> model.getEditor(propName).getFocusTarget().requestFocusInWindow());
        attachKeyBinding();

        if (model.getPropertyType(propName) == Bool.class) {
            JComponent renderedComp = (JComponent) table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, true, true, row, column);

            JPanel container = new JPanel();
            container.setOpaque(true);
            container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 2));
            container.setBackground(renderedComp.getBackground());
            container.setBorder(renderedComp.getBorder());
            container.add(model.getEditor(propName).getEditor());
            return container;
        } else {
            return model.getEditor(propName).getEditor();
        }
    }

    @Override
    public Object getCellEditorValue() {
        model.getEditor(propName).stopEditing();
        return model.getEditor(propName).getValue();
    }

    public boolean stopCellEditing() {
        detachKeyBinding();
        return model.getEditor(propName).stopEditing() && super.stopCellEditing();
    }

    @Override
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
