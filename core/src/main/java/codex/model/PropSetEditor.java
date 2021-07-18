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

        /*Logger.getLogger().info("PropSetEditor.commit:");

        Logger.getLogger().info(" - total model changes: "+entity.model.getChanges());

        final List<String> changedExtProps = getChangedProperties(entity, propFilter);
        Logger.getLogger().info(" - extra model changes: "+changedExtProps);

        Logger.getLogger().info(" > commit just extra prop values");
        try {
            entity.model.commit(true, getChangedProperties(entity, propFilter).toArray(new String[]{}));
        } catch (Exception ignore) {}
        Logger.getLogger().info(" - remain model changes: "+entity.model.getChanges());

        List<String>         savedOvrProps = entity.getOverride();
        Logger.getLogger().info(" - saved OVR:     "+savedOvrProps);

        Map<String, Boolean> modifiedOvrProps = OverrideProperty.getOverrideChanges(entity.model);
        Logger.getLogger().info(" - modified OVR:  "+modifiedOvrProps);

        Map<String, Boolean> commitOvrProps  = getDiff(entity.model, propFilter);
        Map<String, Boolean> protectOvrProps = getDiff(entity.model, propFilter.negate());
        Logger.getLogger().info(" - commit OVR:    "+commitOvrProps);
        Logger.getLogger().info(" - protected OVR: "+protectOvrProps);*/


//        if (!commitOvrProps.isEmpty()) {
//            //noinspection unchecked
//            entity.model.getProperty("OVR")
//                    .getOwnPropValue()
//                    .setValue(OverrideProperty.applyOverrideChanges(
//                            savedOvrProps,
//                            commitOvrProps
//                    ));
//            try {
//                entity.model.commit(true, "OVR");
//            } catch (Exception e) {
//                Logger.getLogger().error(e.getMessage());
//            }
//            entity.model.setValue("OVR", OverrideProperty.applyOverrideChanges(entity.getOverride(), baseOvrProps));
//        }

//        try {
//            entity.model.commit(true, getChangedProperties(entity, propFilter).toArray(new String[]{}));
//        } catch (Exception e) {
//            Logger.getLogger().error(e.getMessage());
//        }
//
//        List<String>         saveOvrProps = entity.getOverride();
//        Map<String, Boolean> baseOvrProps = OverrideProperty.getOverrideChanges(entity.model);
//        Map<String, Boolean> diffOvrProps = getDiff(entity.model, propFilter);
//
//        if (!diffOvrProps.equals(baseOvrProps)) {
//            //noinspection unchecked
//            entity.model.getProperty("OVR")
//                    .getOwnPropValue()
//                    .setValue(OverrideProperty.applyOverrideChanges(
//                            saveOvrProps,
//                            diffOvrProps
//                    ));
//            try {
//                entity.model.commit(true, "OVR");
//            } catch (Exception e) {
//                Logger.getLogger().error(e.getMessage());
//            }
//            entity.model.setValue("OVR", OverrideProperty.applyOverrideChanges(saveOvrProps, baseOvrProps));
//        }
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

        /*final List<String>   changedExtProps  = getChangedProperties(entity, propFilter);
        Map<String, Boolean> modifiedOvrProps = OverrideProperty.getOverrideChanges(entity.model);
        Map<String, Boolean> protectOvrProps  = getDiff(entity.model, propFilter.negate());
        boolean needRevert   = !protectOvrProps.equals(modifiedOvrProps);
        if (!changedExtProps.isEmpty() || needRevert) {
            Logger.getContextLogger(EntityModel.OrmContext.class).debug(
                    "Prepared partial rollback plan:{0}{1}",
                    changedExtProps.isEmpty() ? "" : MessageFormat.format("\n* Extra props: {0}", changedExtProps),
                    needRevert                ? "" : MessageFormat.format("\n* Overridden : revert current:{0} => to protected:{1}",
                            modifiedOvrProps.keySet(), protectOvrProps.keySet()
                    )
            );
            if (!changedExtProps.isEmpty()) {
                entity.model.rollback(changedExtProps.toArray(new String[]{}));
            }
            if (needRevert) {
                entity.model.setValue("OVR", OverrideProperty.applyOverrideChanges(entity.getOverride(), protectOvrProps));
            }
        }*/

        /*Logger.getLogger().info("PropSetEditor.rollback:");

        Logger.getLogger().info(" - total model changes: "+entity.model.getChanges());

        final List<String> changedExtProps = getChangedProperties(entity, propFilter);
        Logger.getLogger().info(" - extra model changes: "+changedExtProps);

        Logger.getLogger().info(" > rollback just extra prop values");
        entity.model.rollback(changedExtProps.toArray(new String[]{}));
        Logger.getLogger().info(" - remain model changes: "+entity.model.getChanges());

        List<String>         savedOvrProps = entity.getOverride();
        Logger.getLogger().info(" - saved OVR:     "+savedOvrProps);

        Map<String, Boolean> modifiedOvrProps = OverrideProperty.getOverrideChanges(entity.model);
        Logger.getLogger().info(" - modified OVR:  "+modifiedOvrProps);

        Map<String, Boolean> protectOvrProps = getDiff(entity.model, propFilter.negate());
        Logger.getLogger().info(" - protected OVR: "+protectOvrProps);


        if (!protectOvrProps.equals(modifiedOvrProps)) {
            List<String>         joinOvrProps = OverrideProperty.applyOverrideChanges(savedOvrProps, protectOvrProps);
            Logger.getLogger().info(" - update OVR:   "+joinOvrProps);
            entity.model.setValue("OVR", joinOvrProps);
        }*/
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
