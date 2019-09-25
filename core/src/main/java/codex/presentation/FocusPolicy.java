package codex.presentation;

import codex.editor.IEditor;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.util.Vector;

/**
 * Политика управления фокусом в презентации редактора.
 */
public class FocusPolicy extends DefaultFocusTraversalPolicy {
    Vector<Component> order;

    /**
     * Конмтруктор польтики.
     * @param order Список компонентов допустимых для фокусировки.
     * Формируется из редакторов свойств методом {@link IEditor#getFocusTarget()}.
     */
    public FocusPolicy(Vector<Component> order) {
        this.order = new Vector<>(order.size());
        this.order.addAll(order);
    }

    /**
     * Вычисление следующего компонета, который должен быть сфокусирован.
     * @param focusCycleRoot Контейнер-владелец компонентов.
     * @param prev Компонент имеющий фокус в данный момент. 
     */
    @Override
    public Component getComponentAfter(Container focusCycleRoot, Component prev) {
        int idx = (order.indexOf(prev) + 1) % order.size();
        return order.get(idx);
    }
    
}
