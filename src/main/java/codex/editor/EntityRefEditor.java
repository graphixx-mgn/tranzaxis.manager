package codex.editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.explorer.ExplorerAccessService;
import codex.explorer.tree.INode;
import codex.explorer.tree.INodeListener;
import codex.mask.EntityFilter;
import codex.mask.IRefMask;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
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
public class EntityRefEditor extends AbstractEditor implements ActionListener, INodeListener {

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
                getValues().forEach((item) -> comboBox.addItem(item));
                comboBox.setSelectedItem(comboBox.getItemAt(0));
                
                comboBox.addActionListener(this);
            }
        });
    }
    
    @SuppressWarnings("unchecked")
    protected List<Entity> getValues() {
        EntityRef<Entity> ref = (EntityRef<Entity>) propHolder.getPropValue();
        Class<? extends Entity> entityClass = ref.getEntityClass();
        ExplorerAccessService EAS = (ExplorerAccessService) ServiceRegistry.getInstance().lookupService(ExplorerAccessService.class);

        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter(entity -> ref.getMask().verify(entity))
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
        comboBox = new JComboBox() {
            @Override
            public void removeAllItems() {
                for (int i = 0; i < getItemCount(); i++) {
                    Object item = getItemAt(i);
                    if (item instanceof Entity) {
                        ((Entity) item).removeNodeListener(EntityRefEditor.this);
                    }
                }
                super.removeAllItems();
            }

            @Override
            public void addItem(Object item) {
                super.addItem(item);
                if (item instanceof Entity) {
                    ((Entity) item).addNodeListener(EntityRefEditor.this);
                }
            }
            
        };
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
                if (Entity.class.isAssignableFrom(value.getClass())) {
                    IRefMask<Entity> mask = ((EntityRef) propHolder.getPropValue()).getMask();
                    EntityFilter.Match match = mask.getEntityMatcher().apply((Entity) value);
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

        List<Entity> values = getValues();

        comboBox.addItem(new NullValue());
        values.forEach((entity) -> {
            if (propHolder.getPropValue().getValue() != null &&
                    entity.getPID().equals(((Entity) propHolder.getPropValue().getValue()).getPID()))
            {
                comboBox.addItem(propHolder.getPropValue().getValue());
                setValue(propHolder.getPropValue().getValue());
            } else {
                comboBox.addItem(entity);
            }
        });
        if (propHolder.getPropValue().getValue() != null && !values.contains(propHolder.getPropValue().getValue())) {
            comboBox.addItem(propHolder.getPropValue().getValue());
            setValue(propHolder.getPropValue().getValue());
        }
        if (propHolder.getPropValue().getValue() == null) {
            IRefMask<Entity> mask = ((EntityRef) propHolder.getPropValue()).getMask();
            Function<Entity, EntityFilter.Match> entityMatcher = mask.getEntityMatcher();
            values.stream()
                    .filter((entity) -> entityMatcher.apply(entity) == EntityFilter.Match.Exact)
                    .findFirst()
                    .ifPresent(entity -> comboBox.setSelectedItem(entity));
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

    @Override
    public void childChanged(INode node) {
        if (comboBox.getSelectedItem() == node) {
            comboBox.revalidate();
            comboBox.repaint();
        }
    }
    
}
