package plugin;

import codex.explorer.tree.INode;
import codex.model.*;
import codex.service.Service;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Str;

import java.util.List;

@EntityDefinition(icon = "/images/plugins.png")
public class PluginProviderOptions extends Service<PluginProvider> {

    private final static String PROP_LOCAL_STORAGE = "local";
    private final static String PROP_CHECK_VALID   = "valid";

    private final IModelListener childModelChecker = new IModelListener() {
        @Override
        public void modelSaved(EntityModel model, List<String> changes) {
            PluginProviderOptions.this.model.updateDynamicProps(PROP_CHECK_VALID);
        }
    };

    public PluginProviderOptions(EntityRef owner, String title) {
        super(owner, title);

        // Properties
        model.addDynamicProp(PROP_LOCAL_STORAGE, new Str(PluginProvider.PLUGIN_LOCAL_DIR.getAbsolutePath()), Access.Select, null);
        model.addDynamicProp(PROP_CHECK_VALID, new Bool(null), Access.Any, () -> {
            return childrenList().stream().allMatch(iNode -> ((Entity) iNode).model.isValid()) ? true : null;
        });
        model.getProperty(PROP_CHECK_VALID).setRequired(true);

        model.getProperty(PROP_CHECK_VALID).addChangeListener((name, oldValue, newValue) -> {
            fireChangeEvent();
        });
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return PluginRegistry.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

    @Override
    public void loadChildren() {}

    @Override
    public void attach(INode child) {
        super.attach(child);
        ((Entity) child).model.addModelListener(childModelChecker);
    }
}
