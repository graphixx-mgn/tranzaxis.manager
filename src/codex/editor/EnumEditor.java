package codex.editor;

import codex.component.button.IButton;
import codex.component.render.DefaultRenderer;
import codex.property.PropertyHolder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EnumSet;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

public class EnumEditor extends AbstractEditor implements ActionListener {
    
    protected JComboBox comboBox;
    
    public EnumEditor(PropertyHolder propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        comboBox = new JComboBox(EnumSet.allOf(((Enum)propHolder.getPropValue().getValue()).getClass()).toArray());
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ( "Panel.background" ), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        
        comboBox.setRenderer(new DefaultRenderer());
        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);
        
        Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
        BasicComboPopup popup = (BasicComboPopup)child;
        popup.setBorder(IButton.PRESS_BORDER);
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(comboBox);
        return container;
    }

    @Override
    public void setValue(Object value) {
        if (!comboBox.getSelectedItem().equals(value)) {
            comboBox.setSelectedItem(value);
        }
    }
    
    @Override
    public void setEditable(boolean editable) {
        comboBox.setEnabled(editable);
    }
    
    @Override
    public boolean isEditable() {
        return comboBox.isEnabled();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        propHolder.setValue(comboBox.getSelectedItem());
    }
    
}
