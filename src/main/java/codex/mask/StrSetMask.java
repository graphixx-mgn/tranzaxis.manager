package codex.mask;

import codex.command.EditorCommand;
import codex.component.button.IButton;
import codex.component.render.GeneralRenderer;
import codex.property.PropertyHolder;
import codex.type.ArrStr;
import codex.utils.ImageUtils;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboPopup;


/**
 * Маска превращающая поле типа {@link ArrStr} в поле выбора значения из списка.
 * Свойство должно иметь список возможных значений начиная с 1-й позиции. Нулевая
 * позиция используется для хранения выбранного значения.
 */
public class StrSetMask extends EditorCommand implements IArrMask, ActionListener {
    
    private final int ROWS_VISIBLE = 6;
    
    private final JComboBox       comboBox;
    private final BasicComboPopup popup;
    
    /**
     * Конструктор маски.
     */
    public StrSetMask() {
        super(
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 18, 18), 
            null,
            (holder) -> {
                List<String> value = ((List<String>) holder.getPropValue().getValue());
                return value != null && value.size() > 1;
            }
        );
        comboBox = new JComboBox();
        comboBox.setRenderer(new GeneralRenderer());
        popup = new BasicComboPopup(comboBox);
        popup.setBorder(IButton.PRESS_BORDER);
    }

    @Override
    public String getFormat() {
        return "{0}";
    }

    @Override
    public List<String> getCleanValue() {
        throw new UnsupportedOperationException();
//        List<String> newValue = new LinkedList<>((List<String>) getContext()[0].getPropValue().getValue());
//        newValue.set(0, "");
//        return newValue;
    }
    
    @Override
    public boolean verify(List<String> value) {
        return true;
    }

    @Override
    public void execute(PropertyHolder context) {
        //Container parent = ((Container) getButton()).getParent();
        
        comboBox.removeActionListener(this);
        comboBox.removeAllItems();
        List<String> value = ((List<String>) context.getPropValue().getValue());
        value.stream().skip(1).forEach((item) -> {
            comboBox.addItem(item);
        });
        comboBox.setSelectedIndex(value.subList(1, value.size()).indexOf(value.get(0)));
        comboBox.addActionListener(this);
        
        Component render = comboBox.getRenderer().getListCellRendererComponent(new JList(), "?", 0, true, true);
//        popup.setPreferredSize(new Dimension(
//                parent.getSize().width,
//                render.getPreferredSize().height * Math.min(
//                        ROWS_VISIBLE,
//                        comboBox.getItemCount()
//                ) + 2
//        ));
//        popup.show(parent, 0, parent.getSize().height);
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        if (event.getSource().equals(comboBox)) {
//            popup.hide();
//            List<String> value = ((List<String>) getContext()[0].getPropValue().getValue());
//            List<String> newValue = new LinkedList<>(value);
//            newValue.set(0, (String) comboBox.getSelectedItem());
//            getContext()[0].setValue(newValue);
        } else {
            //super.actionPerformed(event);
        }
    }

}
