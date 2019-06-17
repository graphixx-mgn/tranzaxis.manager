package codex.component.render;

import codex.editor.IEditor;
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

    public static ComplexCellRenderer newInstance() {
        return new ComplexCellRenderer();
    }

    private ComplexCellRenderer() {
        super();
        setOpaque(true);
        setFont(IEditor.FONT_VALUE);
        setHorizontalTextPosition(SwingConstants.RIGHT);
    }

    @Override
    public void setValue(Object value, String placeholder) {
        ImageIcon icon;
        if (value != null && Iconified.class.isAssignableFrom(value.getClass())) {
            icon = ImageUtils.resize(((Iconified) value).getIcon(), 20, 20);
        } else {
            icon = null;
        }
        setIcon(icon);
        setText(value != null ? value.toString() : placeholder);
    }

}
