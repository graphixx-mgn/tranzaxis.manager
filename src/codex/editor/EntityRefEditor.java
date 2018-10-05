package codex.editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.explorer.ExplorerAccessService;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

/**
 * Редактор свойств типа {@link EntityRef}, представляет собой выпадающий список
 * сущностей, найденный по классу и с учетом фильтра указанных в свойстве.
 */
public class EntityRefEditor extends AbstractEditor implements ActionListener {
    
    private final static ExplorerAccessService EAS = (ExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);
    
    private JComboBox comboBox;

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public EntityRefEditor(PropertyHolder propHolder) {
        super(propHolder);
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
            if (newValue instanceof IComplexType) {
                comboBox.removeActionListener(this);
                comboBox.removeAllItems();
                
                comboBox.addItem(new NullValue());
                for (Object item : getValues()) {
                    comboBox.addItem(item);
                }
                comboBox.setSelectedItem(comboBox.getItemAt(0));
                
                comboBox.addActionListener(this);
            }
        });
    }
    
    protected List<Object> getValues() {
        Class             entityClass  = ((EntityRef) propHolder.getPropValue()).getEntityClass();
        Predicate<Entity> entityFilter = ((EntityRef) propHolder.getPropValue()).getEntityFilter();
        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter(entityFilter)
                    .collect(Collectors.toList())
            : new LinkedList<>();
    }
    
    @Override
    public Box getEditor() {
        comboBox.removeActionListener(this);
        comboBox.removeAllItems();
        
        comboBox.addItem(new NullValue());
        if (!propHolder.getPropValue().isEmpty()) {
            comboBox.addItem(propHolder.getPropValue().getValue());
            setValue(propHolder.getPropValue().getValue());
        }
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
        comboBox.setRenderer(new GeneralRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                Function<Entity, EntityRef.Match> entityMatcher = ((EntityRef) propHolder.getPropValue()).getEntityMatcher();
                if (entityMatcher != null && value instanceof Entity) {
                    EntityRef.Match match = entityMatcher.apply((Entity) value);
                    switch (match) {
                        case Exact:
                            component.setForeground(Color.decode("#1C5F0A"));
                            component.setFont(component.getFont().deriveFont(Font.BOLD));
                            break;
                        case About:
                            component.setForeground(Color.decode("#E8A442"));
                            component.setFont(component.getFont().deriveFont(Font.BOLD));
                            break;
                    }
                }
                return component;
            }
            
        });
        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                rebuildList();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                if (propHolder.getPropValue().getValue() == null) {
                    comboBox.setSelectedItem(comboBox.getItemAt(0));
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {}
        });
        
        Object child = comboBox.getAccessibleContext().getAccessibleChild(0);
        BasicComboPopup popup = (BasicComboPopup)child;
        popup.setBorder(IButton.PRESS_BORDER);
        
        Box container = new Box(BoxLayout.X_AXIS);
        container.add(comboBox);
        return container;
    }
    
    private void rebuildList() {
        comboBox.removeActionListener(EntityRefEditor.this);
        comboBox.removeAllItems();

        List<Object> values = getValues();

        comboBox.addItem(new NullValue());
        for (Object item : values) {
            if (propHolder.getPropValue().getValue() != null && 
                ((Entity) item).model.getPID().equals(((Entity) propHolder.getPropValue().getValue()).model.getPID())) 
            {
                comboBox.addItem(propHolder.getPropValue().getValue());
                setValue(propHolder.getPropValue().getValue());
            } else {
                comboBox.addItem(item);
            }
        }
        if (propHolder.getPropValue().getValue() != null && !values.contains(propHolder.getPropValue().getValue())) {
            comboBox.addItem(propHolder.getPropValue().getValue());
            setValue(propHolder.getPropValue().getValue());
        }
        if (propHolder.getPropValue().getValue() == null) {
            Function<Entity, EntityRef.Match> entityMatcher = ((EntityRef) propHolder.getPropValue()).getEntityMatcher();
            Object suitable = values.stream().filter((entity) -> {
                return entityMatcher.apply((Entity) entity) == EntityRef.Match.Exact;
            }).findFirst().orElse(null);
            if (suitable != null) {
                comboBox.setSelectedItem(suitable);
            }
        }
        comboBox.addActionListener(EntityRefEditor.this);
    }

    @Override
    public void setValue(Object value) {
        if (value == null) {
            comboBox.setSelectedItem(comboBox.getItemAt(0));
            comboBox.setForeground(Color.GRAY);
            comboBox.setFont(FONT_VALUE);
        } else {
            if (!comboBox.getSelectedItem().equals(value)) {
                if (((DefaultComboBoxModel) comboBox.getModel()).getIndexOf(value) == -1) {
                    comboBox.addItem(value);
                }
                comboBox.setSelectedItem(value);
            }
            JList list = ((BasicComboPopup) comboBox.getAccessibleContext().getAccessibleChild(0)).getList();
            Component rendered = comboBox.getRenderer().getListCellRendererComponent(list, value, comboBox.getSelectedIndex(), false, false);
            comboBox.setForeground(rendered.getForeground());
            comboBox.setFont(rendered.getFont());
        }
    }
    
    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        comboBox.setEnabled(editable);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == 0) {
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
