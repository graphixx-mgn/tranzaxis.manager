package codex.component.render;

import codex.editor.IEditor;
import codex.model.Entity;
import codex.presentation.SelectorTableModel;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Реализация рендерера ячеек {@link SelectorTableModel} для типов 
 * {@link IComplexType} в виде текстового представления значения. Если тип реализует
 * интерфейс {@link Iconified}, дополнительно отрисовывается иконка.
 */
class ComplexCellRenderer extends CellRenderer<Object> {
    
    private final static ComplexCellRenderer INSTANCE = new ComplexCellRenderer();
    private final static ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    
    public final static ComplexCellRenderer getInstance() {
        return INSTANCE;
    }
        
    final JLabel label = new JLabel();
    final JLabel state = new JLabel();

    private ComplexCellRenderer() {
        super(BoxLayout.X_AXIS);
        
        label.setFont(IEditor.FONT_VALUE);
        label.setHorizontalTextPosition(SwingConstants.RIGHT);
        
        state.setVerticalAlignment(SwingConstants.TOP);
        state.setVerticalTextPosition(SwingConstants.TOP);
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(state, BorderLayout.WEST);
        panel.add(label, BorderLayout.CENTER);
        add(panel);
    }

    @Override
    public void setValue(Object value) {
        ImageIcon icon;
        
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            if (value instanceof Entity && !((Entity) value).model.isValid()) {
                icon = ImageUtils.resize(ImageUtils.combine(((Entity) value).getIcon(),ICON_INVALID), 18, 18);
            } else {
                icon = ImageUtils.resize(((Iconified) value).getIcon(), 18, 18);
            }
        } else {
            icon = null;
        }
        label.setIcon(icon);
        label.setText(value != null ? value.toString() : IEditor.NOT_DEFINED);
    }
    
    @Override
    public Dimension getPreferredSize() {
        return label.getPreferredSize();
    }

    @Override
    public void setForeground(Color color) {
        label.setForeground(color);
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        if (label.getIcon() != null && !enabled) {
            label.setIcon(ImageUtils.grayscale((ImageIcon) label.getIcon()));
        }
    }
    
}
