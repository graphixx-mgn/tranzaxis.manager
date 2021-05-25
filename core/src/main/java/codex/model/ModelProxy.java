package codex.model;

import codex.editor.IEditor;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ModelProxy extends ParamModel {

    private final AbstractModel     parentModel;
    private final Predicate<String> propFilter;

    public ModelProxy(AbstractModel parentModel, Predicate<String> propFilter) {
        this.parentModel = parentModel;
        this.propFilter  = propFilter;

        List<String> mappedProps = parentModel.getProperties(Access.Any).stream()
                .filter(propFilter)
                .collect(Collectors.toList());

        mappedProps.stream()
                .map(parentModel::getProperty)
                .forEach(propertyHolder -> {
                    addProperty(propertyHolder);
                    addPropertyGroup(parentModel.getPropertyGroup(propertyHolder.getName()), propertyHolder.getName());
                });
    }

    @Override
    public IEditor getEditor(String name) {
        return parentModel.getEditor(name);
    }

    public Predicate<String> getPropertyFilter() {
        return propFilter;
    }

}
