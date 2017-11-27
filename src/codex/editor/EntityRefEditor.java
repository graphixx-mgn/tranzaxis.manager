package codex.editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import static codex.editor.IEditor.FONT_VALUE;
import codex.explorer.ExplorerAccessService;
import codex.explorer.IExplorerAccessService;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

/**
 * Редактор свойств типа {@link EntityRef}, представляет собой выпадающий список
 * сущностей, найденный по классу и с учетом фильтра указанных в свойстве.
 */
public class EntityRefEditor extends AbstractEditor implements ActionListener {
    
    private final static IExplorerAccessService EAS = (IExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private JComboBox comboBox;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public EntityRefEditor(PropertyHolder propHolder) {
        super(propHolder);
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
            if (newValue instanceof IComplexType) {
                comboBox.removeAllItems();
                for (Object item : getValues()) {
                    comboBox.addItem(item);
                }
                comboBox.addItem(new NullValue());
                comboBox.setSelectedItem(comboBox.getItemAt(comboBox.getItemCount()-1));
            }
        });
    }
    
    private Object[] getValues() {
        Class             entityClass  = ((EntityRef) propHolder.getPropValue()).getEntityClass();
        Predicate<Entity> entityFilter = ((EntityRef) propHolder.getPropValue()).getEntityFilter();
        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter(entityFilter)
                    .collect(Collectors.toList())
                    .toArray()
            : new Object[]{};
    }
    
    @Override
    public Box getEditor() {
        Object selected = comboBox.getSelectedItem();
        comboBox.removeActionListener(this);
        comboBox.removeAllItems();
        
        boolean found = false;
        for (Object item : getValues()) {
            comboBox.addItem(item);
            if (item == selected) {
                found = true;
            }
        }
        comboBox.addItem(new NullValue());
        
        setValue(found ? selected : null);
        propHolder.setValue(found ? selected : null);
        comboBox.addActionListener(this);
        return super.getEditor();
    }

    @Override
    public Box createEditor() {        
        comboBox = new JComboBox();
        comboBox.addItem(new NullValue());
        
        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ("Panel.background"), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        
        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new GeneralRenderer());
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
        if (value == null) {
            comboBox.setSelectedItem(comboBox.getItemAt(comboBox.getItemCount()-1));
        } else {
            if (!comboBox.getSelectedItem().equals(value)) {
                comboBox.setSelectedItem(value);
            }
        }
        comboBox.setForeground(value == null ? Color.GRAY : IEditor.COLOR_NORMAL);
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        comboBox.setEnabled(editable);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == comboBox.getItemCount()-1) {
            propHolder.setValue(null);
        } else {
            if (!comboBox.getSelectedItem().equals(propHolder.getPropValue().getValue())) {
                propHolder.setValue(comboBox.getSelectedItem());
            }
        }
    }
    
    @Override
    public Component getFocusTarget() {
        return comboBox;
    }
    
}
