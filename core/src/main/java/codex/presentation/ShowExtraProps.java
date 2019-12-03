package codex.presentation;

import codex.command.CommandStatus;
import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.log.Logger;
import codex.model.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ShowExtraProps extends EntityCommand<Entity> {

    private final static ImageIcon IMAGE_WARN = ImageUtils.resize(ImageUtils.getByPath("/images/warn.png"), 20, 20);

    ShowExtraProps() {
        super(
                "extra",null,
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EditorPresentation.class, "command@extra"),
                null
        );
        Function<List<Entity>, CommandStatus> defaultActivator = activator;
        activator = entities -> {
            Entity context = entities.get(0);
            boolean hasInvalidProp = context.model.getProperties(Access.Any).stream()
                    .filter(context.model::isPropertyExtra)
                    .anyMatch(propName -> !context.model.getProperty(propName).isValid());
            return new CommandStatus(
                    defaultActivator.apply(entities).isActive(),
                    hasInvalidProp ? ImageUtils.combine(getIcon(), IMAGE_WARN, SwingConstants.SOUTH_EAST) : getIcon()
            );
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Entity context, Map<String, IComplexType> params) {
        DialogButton btnSubmit = Dialog.Default.BTN_OK.newInstance();
        DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

        List<String> extraPropNames = getExtraProperties(context.model);

        ParamModel paramModel = new ParamModel();

        extraPropNames.stream()
                .map(context.model::getProperty)
                .forEach(propertyHolder -> {
                    paramModel.addProperty(propertyHolder);
                    paramModel.addPropertyGroup(context.model.getPropertyGroup(propertyHolder.getName()), propertyHolder.getName());
                });

        EntityModel childModel  = context.model;
        EntityModel parentModel = ((Entity) context.getParent()).model;
        List<String> overrideProps = context.getOverrideProps(parentModel).stream()
                .filter(extraPropNames::contains)
                .collect(Collectors.toList());

        if (!overrideProps.isEmpty()) {
            overrideProps.forEach((propName) -> {
                if (paramModel.getEditor(propName).getCommands().stream().noneMatch((command) -> command instanceof OverrideProperty)) {
                    paramModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName, paramModel.getEditor(propName)));
                }
            });
        }

        Predicate<Entity> hasChanges = entity -> entity.model.getChanges().stream()
                .anyMatch(changedProp ->
                        extraPropNames.contains(changedProp) || (
                                !overrideProps.isEmpty()   &&
                                 changedProp.equals("OVR") &&
                                 OverrideProperty.getOverrideChanges(entity.model).entrySet().stream()
                                     .anyMatch(entry -> entry.getValue() && extraPropNames.contains(entry.getKey()))
                        )
                );

        btnSubmit.setEnabled(hasChanges.test(context));
        context.model.addChangeListener((name, oldValue, newValue) -> btnSubmit.setEnabled(hasChanges.test(context)));

        List<String> savedOverridden = context.getOverride();
        Map<String, Boolean> initialOverriddenDiff = OverrideProperty.getOverrideChanges(childModel);

        Dialog dialog = new Dialog(
                null,
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EditorPresentation.class, "extender@title"),
                new JPanel(new BorderLayout()) {{
                    add(new EditorPage(paramModel), BorderLayout.NORTH);
                    setBorder(new CompoundBorder(
                            new EmptyBorder(10, 5, 5, 5),
                            new TitledBorder(new LineBorder(Color.LIGHT_GRAY, 1), context.toString())
                    ));
                }},
                (event) -> {
                    if (event.getID() == Dialog.OK) {
                        acceptChanges(context.model, overrideProps, savedOverridden, initialOverriddenDiff);
                    } else {
                        declineChanges(context.model, overrideProps, savedOverridden, initialOverriddenDiff);
                    }
                },
                btnSubmit, btnCancel
        ){
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(900, super.getPreferredSize().height);
            }
        };
        dialog.setResizable(false);
        dialog.setVisible(true);
    }

    private List<String> getExtraProperties(EntityModel model) {
        return model.getProperties(Access.Any).stream()
                    .filter(model::isPropertyExtra)
                    .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void acceptChanges(EntityModel model, List<String> overrideProps, List<String> savedOverridden, Map<String, Boolean> initialOverriddenDiff) {
        try {
            List<String> changes = model.getChanges();
            model.commit(true, getExtraProperties(model).stream()
                    .filter(propName -> !model.isPropertyDynamic(propName) && changes.contains(propName))
                    .collect(Collectors.toList())
                    .toArray(new String[]{}));
        } catch (Exception e) {
            Logger.getLogger().error(e.getMessage());
        }

        if (!overrideProps.isEmpty()) {
            Map<String, Boolean> currentOverriddenDiff = OverrideProperty.getOverrideChanges(model);
            if (!initialOverriddenDiff.equals(currentOverriddenDiff)) {
                model.getProperty("OVR").getOwnPropValue().setValue(
                        OverrideProperty.applyOverrideChanges(
                                savedOverridden,
                                currentOverriddenDiff.entrySet().stream()
                                        .filter(entry -> getExtraProperties(model).contains(entry.getKey()))
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue
                                        ))
                        )
                );
                try {
                    model.commit(true, "OVR");
                } catch (Exception e) {
                    Logger.getLogger().error(e.getMessage());
                }
                model.setValue("OVR", OverrideProperty.applyOverrideChanges(savedOverridden, currentOverriddenDiff));
            }
        }
    }

    private void declineChanges(EntityModel model, List<String> overrideProps, List<String> savedOverridden, Map<String, Boolean> initialOverriddenDiff) {
        model.rollback(getExtraProperties(model).toArray(new String[]{}));

        if (!overrideProps.isEmpty()) {
            Map<String, Boolean> currentOverriddenDiff = OverrideProperty.getOverrideChanges(model);
            if (!initialOverriddenDiff.equals(currentOverriddenDiff)) {
                model.setValue("OVR", OverrideProperty.applyOverrideChanges(savedOverridden, initialOverriddenDiff));
            }
        }
    }

}
