package codex.editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import static codex.editor.IEditor.FONT_VALUE;
import codex.explorer.ExplorerAccessService;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.type.EntityRef;
import codex.type.IComplexType;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
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
    
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public EntityRefEditor(PropertyHolder propHolder) {
        super(propHolder);
        propHolder.addChangeListener((String name, Object oldValue, Object newValue) -> {
            if (newValue instanceof IComplexType) {
                comboBox.removeAllItems();
                comboBox.addItem(new NullValue());
                for (Object item : getValues()) {
                    comboBox.addItem(item);
                }
                comboBox.setSelectedItem(comboBox.getItemAt(0));
            }
        });
    }
    
    private List<Object> getValues() {
        Class             entityClass  = ((EntityRef) propHolder.getPropValue()).getEntityClass();
        Predicate<Entity> entityFilter = ((EntityRef) propHolder.getPropValue()).getEntityFilter();
        return entityClass != null ? EAS.getEntitiesByClass(entityClass)
                    .stream()
                    .filter(entityFilter)
                    .filter(distinctByKey((entity) -> {
                        return entity.model.getPID();
                    }))
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
        comboBox.setRenderer(new GeneralRenderer());
        comboBox.addFocusListener(this);
        comboBox.addActionListener(this);
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
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
                comboBox.addActionListener(EntityRefEditor.this);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}

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

    @Override
    public void setValue(Object value) {
        if (value == null) {
            comboBox.setSelectedItem(comboBox.getItemAt(0));
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
