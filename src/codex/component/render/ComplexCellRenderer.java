package codex.component.render;

import codex.editor.IEditor;
import codex.model.Entity;
import codex.presentation.SelectorTableModel;
import codex.type.IComplexType;
import codex.type.Iconified;
import codex.utils.ImageUtils;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;

/**
 * Реализация рендерера ячеек {@link SelectorTableModel} для типов 
 * {@link IComplexType} в виде текстового представления значения. Если тип реализует
 * интерфейс {@link Iconified}, дополнительно отрисовывается иконка.
 */
final class ComplexCellRenderer extends CellRenderer<Object> {
    
    private final static ComplexCellRenderer INSTANCE = new ComplexCellRenderer();
    private final static ImageIcon ICON_INVALID = ImageUtils.getByPath("/images/warn.png");
    
    public final static ComplexCellRenderer getInstance() {
        return INSTANCE;
    }

    private ComplexCellRenderer() {
        super();
        setOpaque(true);
        setFont(IEditor.FONT_VALUE);
        setHorizontalTextPosition(SwingConstants.RIGHT);
    }

    @Override
    public void setValue(Object value) {
        ImageIcon icon;
        
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            if (value instanceof Entity && !((Entity) value).model.isValid()) {
                icon = ImageUtils.resize(ImageUtils.combine(((Iconified) value).getIcon(),ICON_INVALID), 18, 18);
            } else {
                icon = ImageUtils.resize(((Iconified) value).getIcon(), 18, 18);
            }
        } else {
            icon = null;
        }
        setIcon(icon);
        setText(value != null ? value.toString() : IEditor.NOT_DEFINED);
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        if (getIcon() != null && !enabled) {
            setIcon(ImageUtils.grayscale((ImageIcon) getIcon()));
        }
    }
    
}
