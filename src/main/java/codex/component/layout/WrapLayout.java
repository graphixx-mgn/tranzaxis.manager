package codex.component.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Реализация менеджера компоновки с плавающими элементами. Элементы фиксированного
 * размера выстраиваются в линию до достижения правой границы контейнера, затем
 * переносятся на новую линию и т.д. При изменении размера контейнера все элементы
 * динамически перестраиваются.
 */
public class WrapLayout extends FlowLayout {
	private Dimension preferredLayoutSize;

        /**
         * Конструктор менеджера.
         */
	public WrapLayout() {
            super();
	}

        /**
         * Конструктор менеджера.
         * @param align Флаг выравнивания строки элементов:
         * ({@link FlowLayout#LEFT}, {@link FlowLayout#CENTER}, {@link FlowLayout#RIGHT}).
         */
	public WrapLayout(int align) {
            super(align);
	}

        /**
         * Конструктор менеджера.
         * @param align Флаг выравнивания строки элементов:
         * ({@link FlowLayout#LEFT}, {@link FlowLayout#CENTER}, {@link FlowLayout#RIGHT}).
         * @param hgap Горизонтальный отступ.
         * @param vgap Вертикальный отступ.
         */
	public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
	}

	@Override
	public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
	}

	@Override
	public Dimension minimumLayoutSize(Container target) {
            Dimension minimum = layoutSize(target, false);
            minimum.width -= (getHgap() + 1);
            return minimum;
	}

	private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
		Container container = target;

		while (container.getSize().width == 0 && container.getParent() != null) {
                    container = container.getParent();
		}
		int targetWidth = container.getSize().width;
		if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }
		int hgap = getHgap();
		int vgap = getVgap();
                Insets insets = target.getInsets();
		int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
		int maxWidth = targetWidth - horizontalInsetsAndGap;

		Dimension dim = new Dimension(0, 0);
		int rowWidth = 0;
		int rowHeight = 0;

		int nmembers = target.getComponentCount();
		for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                    	if (rowWidth != 0) {
                            rowWidth += hgap;
                        }
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
		}
		addRow(dim, rowWidth, rowHeight);
		dim.width += horizontalInsetsAndGap;
		dim.height += insets.top + insets.bottom + vgap * 2;
		Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
		if (scrollPane != null && target.isValid()) {
                    dim.width -= (hgap + 1);
		}
		return dim;
            }
	}

	private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0) {
                dim.height += getVgap();
            }
            dim.height += rowHeight;
	}
        
}
