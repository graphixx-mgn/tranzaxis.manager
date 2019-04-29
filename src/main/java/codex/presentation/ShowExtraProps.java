package codex.presentation;

import codex.command.EntityCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.model.Access;
import codex.model.Entity;
import codex.model.ParamModel;
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

        List<PropertyHolder> extraProps = context.model.getProperties(Access.Any).stream()
                .filter(context.model::isPropertyExtra)
                .map(context.model::getProperty)
                .collect(Collectors.toList());

        Predicate<Entity> changedExtraProps = entity -> {
              List<String> changes = entity.model.getChanges();
              changes.retainAll(extraProps.stream().map(PropertyHolder::getName).collect(Collectors.toList()));
              return !changes.isEmpty();
        };

        ParamModel paramModel = new ParamModel();
        extraProps.forEach(paramModel::addProperty);

        Listener editorListener = new Listener(context, paramModel);
        IPropertyChangeListener buttonListener = (name, oldValue, newValue) -> btnSubmit.setEnabled(changedExtraProps.test(context));

        btnSubmit.setEnabled(changedExtraProps.test(context));
        extraProps.forEach(propertyHolder -> {
            paramModel.getEditor(propertyHolder.getName()).getLabel().setText(
                    propertyHolder.getTitle() + (context.model.getChanges().contains(propertyHolder.getName()) ? " *" : "")
            );
            propertyHolder.addChangeListener(editorListener);
            propertyHolder.addChangeListener(buttonListener);
        });

        new Dialog(
                null,
                ImageUtils.getByPath("/images/param.png"),
                Language.get(EditorPresentation.class, "extender@title"),
                new JPanel(new BorderLayout()) {{
                    add(new EditorPage(paramModel));
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
        ).setVisible(true);

        extraProps.forEach(propertyHolder -> {
            propertyHolder.removeChangeListener(paramModel);
            propertyHolder.removeChangeListener(editorListener);
            propertyHolder.removeChangeListener(buttonListener);
        });
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
