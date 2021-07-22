package codex.model;

import codex.component.dialog.Dialog;
import codex.log.Logger;
import codex.presentation.EditorPage;
import codex.property.IPropertyChangeListener;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PropSetEditor extends Dialog {

    private static List<String> getChangedProperties(Entity entity, Predicate<String> propFilter) {
        return entity.model.getChanges().stream()
                .filter(propFilter)
                .filter(((Predicate<String>) entity.model::isPropertyDynamic).negate())
                .collect(Collectors.toList());
    }

    private static Map<String, Boolean> getDiff(EntityModel model, Predicate<String> propFilter) {
        return OverrideProperty.getOverrideChanges(model).entrySet().stream()
                .filter(entry -> propFilter.test(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static void commit(Entity entity, Predicate<String> propFilter) {
        final List<String>   changedExtProps  = getChangedProperties(entity, propFilter);
        Map<String, Boolean> commitOvrProps   = getDiff(entity.model, propFilter);
        Map<String, Boolean> protectOvrProps  = getDiff(entity.model, propFilter.negate());
        boolean              needCommitOTH    = !changedExtProps.isEmpty();
        boolean              needCommitOVR    = !commitOvrProps.isEmpty();

        if (needCommitOTH) {
            try {
                entity.model.commit(true, changedExtProps.toArray(new String[]{}));
            } catch (Exception ignore) {}
        }
        if (needCommitOVR) {
            //noinspection unchecked
            entity.model.getProperty("OVR")
                    .getOwnPropValue()
                    .setValue(OverrideProperty.applyOverrideChanges(
                            entity.getOverride(),
                            commitOvrProps
                    ));
            try {
                entity.model.commit(true, "OVR");
            } catch (Exception ignore) {}
            entity.model.setValue("OVR", OverrideProperty.applyOverrideChanges(entity.getOverride(), protectOvrProps));
        }
    }

    private static void rollback(Entity entity, Predicate<String> propFilter) {
        final List<String>   changedExtProps  = getChangedProperties(entity, propFilter);
        Map<String, Boolean> modifiedOvrProps = OverrideProperty.getOverrideChanges(entity.model);
        Map<String, Boolean> protectOvrProps  = getDiff(entity.model, propFilter.negate());
        boolean              needRevertOTH    = !changedExtProps.isEmpty();
        boolean              needRevertOVR    = !protectOvrProps.equals(modifiedOvrProps);

        if (needRevertOTH) {
            entity.model.rollback(changedExtProps.toArray(new String[]{}));
        }
        if (needRevertOVR) {
            entity.model.setValue("OVR", OverrideProperty.applyOverrideChanges(entity.getOverride(), protectOvrProps));
        }
    }

    private final Window     owner;
    private final Entity     entity;
    private final ModelProxy proxyModel;
    private final Predicate<String> propFilter;

    private final ComponentAdapter resizeHandler = new ComponentAdapter() {
        @Override
        public void componentHidden(ComponentEvent e) {
            pack();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            pack();
        }
    };
    private final IPropertyChangeListener changeHandler = (name, oldValue, newValue) -> getButton(Dialog.OK).setEnabled((
                !getChangedProperties(getEntity(), getPropFilter()).isEmpty() ||
                 getDiff(getEntity().model, getPropFilter()).entrySet().stream().anyMatch(Map.Entry::getValue)
            ) && getEntity().model.isValid()
    );

    public PropSetEditor(ImageIcon icon, String title, Entity entity, Predicate<String> propFilter) {
        super(
                Dialog.findNearestWindow(),
                icon,
                title,
                new JPanel(),
                event -> {
                    if (event.getID() == Dialog.OK) {
                        commit(entity, propFilter);
                    } else {
                        rollback(entity, propFilter);
                    }
                },
                Dialog.Default.BTN_OK.newInstance(),
                Dialog.Default.BTN_CANCEL.newInstance()
        );
        this.owner      = Dialog.findNearestWindow();
        this.entity     = entity;
        this.proxyModel = new ModelProxy(entity.model, propFilter);
        this.propFilter = propFilter;

        setContent(new JPanel(new BorderLayout()) {{
            add(new EditorPage(proxyModel), BorderLayout.NORTH);
        }});
        getButton(Dialog.OK).setEnabled(false);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);
    }

    public final void open() {
        setVisible(true);
    }

    private Entity getEntity() {
        return entity;
    }

    private Predicate<String> getPropFilter() {
        return propFilter;
    }

    public Dimension getPreferredSize() {
        return new Dimension(
                owner != null && owner instanceof Dialog? owner.getPreferredSize().width - 20 : 700,
                super.getPreferredSize().height
        );
    }

    @Override
    public final void setVisible(boolean visible) {
        if (visible) {
            proxyModel.getProperties(Access.Any).stream()
                    .map(proxyModel::getEditor)
                    .forEach(propEditor -> propEditor.getEditor().addComponentListener(resizeHandler));
            proxyModel.addChangeListener(changeHandler);
            entity.model.addChangeListener(changeHandler);
        } else {
            proxyModel.getProperties(Access.Any).stream()
                    .map(proxyModel::getEditor)
                    .forEach(propEditor -> propEditor.getEditor().removeComponentListener(resizeHandler));
            entity.model.removeChangeListener(changeHandler);
        }
        super.setVisible(visible);
    }
}
