package codex.component.render;

import codex.editor.IEditor;
import codex.presentation.SelectorTableModel;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JLabel;

/**
 * Реализация рендерера ячеек {@link SelectorTableModel} для типов 
 * {@link IComplexType} в виде текстового представления значения. Если тип реализует
 * интерфейс {@link Iconified}, дополнительно отрисовывается иконка.
 */
class ComplexCellRenderer extends CellRenderer<Object> {
    
    private final static ComplexCellRenderer INSTANCE = new ComplexCellRenderer();
    
    public final static ComplexCellRenderer getInstance() {
        return INSTANCE;
    }
        
    private final JLabel label = new JLabel();

    private ComplexCellRenderer() {
        super(BoxLayout.X_AXIS);
        add(label);
        label.setFont(IEditor.FONT_VALUE); 
    }

    @Override
    public void setValue(Object value) {
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            label.setIcon(ImageUtils.resize(((Iconified) value).getIcon(), 18, 18));
        } else {
            label.setIcon(null);
        }
        label.setText(value != null ? value.toString() : IEditor.NOT_DEFINED);
        label.setForeground(value != null ? IEditor.COLOR_NORMAL : IEditor.COLOR_DISABLED);
    }

    @Override
    public Dimension getPreferredSize() {
        return label.getPreferredSize();
    }

}
