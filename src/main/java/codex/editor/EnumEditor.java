package codex.editor;

import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.property.PropertyHolder;
import codex.type.Enum;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicComboPopup;

/**
 * Редактор свойств типа {@link Enum}, представляет собой выпадающий список элементов.
 * Если свойство содержит перечисление, реализующее интерфейс {@link Iconified}, 
 * отображаются также и иконки для каждого элемента.
 */
public class EnumEditor<T extends java.lang.Enum> extends AbstractEditor<Enum<T>, T> implements ActionListener {

    private final static ImageIcon ICON_NULL = ImageUtils.resize(ImageUtils.getByPath("/images/clearval.png"), 17, 17);
    
    protected JComboBox<T> comboBox;
    
    /**
     * Конструктор редактора.
     * @param propHolder Редактируемое свойство.
     */
    public EnumEditor(PropertyHolder<Enum<T>, T> propHolder) {
        super(propHolder);
    }

    @Override
    public Box createEditor() {
        comboBox = new JComboBox<>(((Class<T>)propHolder.getPropValue().getValue().getClass()).getEnumConstants());

        UIManager.put("ComboBox.border", new BorderUIResource(
                new LineBorder(UIManager.getColor ( "Panel.background" ), 1))
        );
        SwingUtilities.updateComponentTreeUI(comboBox);
        
        comboBox.setFont(FONT_VALUE);
        comboBox.setRenderer(new GeneralRenderer<T>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean hasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                if (Enum.isUndefined(value)) {
                    label.setIcon(EnumEditor.this.isEditable() ? ICON_NULL : ImageUtils.grayscale(ICON_NULL));
                    label.setForeground(Color.GRAY);
                }
                return label;
            }
        });
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
    public void setValue(T value) {
        if (!comboBox.getSelectedItem().equals(value)) {
            comboBox.setSelectedItem(value);
        }
        if (Enum.isUndefined(value)) {
            comboBox.setForeground(Color.GRAY);
            comboBox.setFont(FONT_VALUE);
        } else {
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

    /**
     * Метод, вызываемый при выборе элемента списка, изменяет внутреннее значение
     * свойства.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        if (!comboBox.getSelectedItem().equals(propHolder.getPropValue().getValue())) {
            propHolder.setValue(comboBox.getItemAt(comboBox.getSelectedIndex()));
        }
    }

    @Override
    public Component getFocusTarget() {
        return comboBox;
    }
}
