package codex.editor;

import codex.component.button.IButton;
import codex.property.PropertyHolder;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
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
        comboBox = new JComboBox(propHolder.getType().getEnumConstants());
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ( "Panel.background" ), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        comboBox.setRenderer(new ComboBoxRenderer());
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
    public void actionPerformed(ActionEvent event) {
        propHolder.setValue(comboBox.getSelectedItem());
    }
    
    private class ComboBoxRenderer extends JLabel implements ListCellRenderer {
        
        public ComboBoxRenderer() {
            setOpaque(true);
            setIconTextGap(10);
            setVerticalAlignment(CENTER);
            setBorder(new EmptyBorder(1, 4, 1, 2));
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value.toString());
            setBackground(isSelected ? IButton.PRESS_COLOR : list.getBackground());
            
            if (Iconified.class.isAssignableFrom(value.getClass())) {
                setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 15, 15));
            }
            return this;
        }
    
    }
    
}
