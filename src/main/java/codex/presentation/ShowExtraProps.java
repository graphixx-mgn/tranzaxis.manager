package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.editor.IEditor;
import codex.model.*;
import codex.property.IPropertyChangeListener;
import codex.property.PropertyHolder;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ShowExtraProps extends EntityCommand<Entity> {

    ShowExtraProps() {
        super(
                "extra",null,
                ImageUtils.resize(ImageUtils.getByPath("/images/param.png"), 28, 28),
                Language.get(EditorPresentation.class, "command@extra"),
                null
        );
    }

    @Override
    public void execute(Entity context, Map<String, IComplexType> params) {
        DialogButton btnSubmit = Dialog.Default.BTN_OK.newInstance();
        DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

        List<String> extraPropNames = context.model.getProperties(Access.Any).stream()
                .filter(context.model::isPropertyExtra)
                .collect(Collectors.toList());

        List<PropertyHolder> extraProps = extraPropNames.stream()
                .map(context.model::getProperty)
                .collect(Collectors.toList());

        ParamModel paramModel = new ParamModel();
        extraProps.forEach(propertyHolder -> {
            paramModel.addProperty(propertyHolder);
            paramModel.addPropertyGroup(context.model.getPropertyGroup(propertyHolder.getName()), propertyHolder.getName());
        });

        EntityModel childModel  = context.model;
        EntityModel parentModel = ((Entity) context.getParent()).model;
        List<String> overrideProps = parentModel.getProperties(Access.Edit)
                .stream()
                .filter(
                        propName ->
                                extraPropNames.contains(propName) &&
                                childModel.hasProperty(propName) &&
                                        !childModel.isPropertyDynamic(propName) &&
                                        !EntityModel.SYSPROPS.contains(propName) &&
                                        parentModel.getPropertyType(propName) == childModel.getPropertyType(propName)
                ).collect(Collectors.toList());
        if (!overrideProps.isEmpty()) {
            overrideProps.forEach((propName) -> {
                if (!paramModel.getEditor(propName).getCommands().stream().anyMatch((command) -> {
                    return command instanceof OverrideProperty;
                })) {
                    paramModel.getEditor(propName).addCommand(new OverrideProperty(parentModel, childModel, propName, paramModel.getEditor(propName)));
                }
            });
        }

        Predicate<Entity> changedExtraProps = entity -> entity.model.getChanges().stream()
                .anyMatch(changedProp ->
                        extraPropNames.contains(changedProp) || (
                                !overrideProps.isEmpty() && changedProp.equals("OVR")
                        )
                );

        Listener editorListener = new Listener(context, paramModel);
        IPropertyChangeListener buttonListener = (name, oldValue, newValue) -> btnSubmit.setEnabled(changedExtraProps.test(context));

        extraProps.forEach(propertyHolder -> {
            IEditor editor = paramModel.getEditor(propertyHolder.getName());
            editor.getLabel().setText(
                    propertyHolder.getTitle() + (context.model.getChanges().contains(propertyHolder.getName()) ? " *" : "")
            );
            propertyHolder.addChangeListener(editorListener);
            propertyHolder.addChangeListener(buttonListener);
        });
        if (!overrideProps.isEmpty()) {
            context.model.getProperty("OVR").addChangeListener(buttonListener);
        }

        btnSubmit.setEnabled(changedExtraProps.test(context));
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
                        //
                    } else {
                        context.model.rollback(
                                extraProps.stream()
                                        .map(propertyHolder -> propertyHolder.getName())
                                        .collect(Collectors.toList())
                                        .toArray(new String[]{})
                        );
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

        extraProps.forEach(propertyHolder -> {
            propertyHolder.removeChangeListener(paramModel);
            propertyHolder.removeChangeListener(editorListener);
            propertyHolder.removeChangeListener(buttonListener);
        });
        if (!overrideProps.isEmpty()) {
            context.model.getProperty("OVR").removeChangeListener(buttonListener);
        }
    }


    class Listener implements IPropertyChangeListener {

        private final Entity     context;
        private final ParamModel paramModel;
        Listener(Entity context, ParamModel paramModel) {
            this.context    = context;
            this.paramModel = paramModel;
        }

        @Override
        public void propertyChange(String name, Object oldValue, Object newValue) {
            paramModel.getEditor(name).getLabel().setText(
                    paramModel.getProperty(name).getTitle() + (context.model.getChanges().contains(name) ? " *" : "")
            );
        }
    }
}
