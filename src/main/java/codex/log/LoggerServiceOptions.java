package codex.log;

import codex.context.ContextView;
import codex.context.ContextType;
import codex.context.IContext;
import codex.context.RootContext;
import codex.editor.MapEditor;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.*;
import codex.presentation.EditorPage;
import codex.presentation.SelectorTreeTable;
import codex.service.LocalServiceOptions;
import codex.type.Bool;
import codex.type.EntityRef;
import codex.type.Enum;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.atteo.classindex.ClassIndex;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class LoggerServiceOptions extends LocalServiceOptions<LogManagementService> {

    @Bootstrap.BootProperty
    final static String PROP_LOG_LEVELS = "contextLevels";
    final static String PROP_CTX_LEVEL  = "level";

    private final ContextView   root = new ContextView(RootContext.class);
    private final NodeTreeModel treeModel = new NodeTreeModel(root);
    
    public LoggerServiceOptions(EntityRef owner, String title) {
        super(owner, title);
        setIcon(ImageUtils.getByPath("/images/lamp.png"));
        List<Class<? extends IContext>> contexts = getContexts();

        // Properties
        Map<ContextView, Level> contextLevels = contexts.stream()
                .collect(Collectors.toMap(
                        ContextView::new,
                        ctxClass -> {
                            boolean isOption = ctxClass.getAnnotation(LoggingSource.class).debugOption();
                            return isOption ? Level.Off : Level.Debug;
                        }
                ));

        model.addUserProp(PROP_LOG_LEVELS,
                new codex.type.Map<>(
                        ContextType.class,
                        new Enum<Level>(Level.class){}.getClass(),
                        contextLevels
                ),
                false, Access.Select
        );
        ((MapEditor) model.getEditor(PROP_LOG_LEVELS)).setMode(MapEditor.EditMode.ModifyPermitted);
        model.getEditor(PROP_LOG_LEVELS).setVisible(false);

        // Build context tree
        fillContext(contexts);

        // Embedded context tree selector
        SelectorTreeTable<ContextView> treeTable = new SelectorTreeTable<>((ContextView) treeModel.getRoot(), ContextView.class);
        treeTable.setPropertyEditable(PROP_CTX_LEVEL);

        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setViewportView(treeTable);
        scrollPane.setBorder(new CompoundBorder(
                new EmptyBorder(5, 5, 5, 5),
                new MatteBorder(1, 1, 1, 1, Color.GRAY)
        ));

        final JPanel embeddedSelector = new JPanel(new BorderLayout());
        embeddedSelector.add(scrollPane);

        embeddedSelector.setBorder(new TitledBorder(
                new LineBorder(Color.GRAY, 1),
                Language.get("context@tree")
        ));

        EditorPage editorPage = getEditorPage();
        GridBagConstraints gbc = new GridBagConstraints() {{
            insets = new Insets(5, 10, 5, 10);
            fill = GridBagConstraints.HORIZONTAL;
            gridwidth = 2;
            gridx = 0;
            gridy = (editorPage.getComponentCount() - 2) / 2 + 1;
        }};
        editorPage.add(embeddedSelector, gbc);

        model.addModelListener(new IModelListener() {
            @Override
            public void modelRestored(EntityModel model, List<String> changes) {
                if (changes.contains(PROP_LOG_LEVELS)) {
                    Map<ContextView, Level> levels = getLevels();
                    for (INode node : treeModel) {
                        ContextView ctxView = (ContextView) node;
                        Level ctxLevel = levels.get(ctxView);
                        boolean isOption = ctxView.getContextClass().getAnnotation(LoggingSource.class).debugOption();
                        ctxView.model.setValue(PROP_CTX_LEVEL, isOption ? ctxLevel == Level.Debug : ctxLevel);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<ContextView, Level> getLevels() {
        return (Map<ContextView, Level>) model.getUnsavedValue(PROP_LOG_LEVELS);
    }

    private void setLevels(Map<ContextView, Level> levels) {
        model.setValue(PROP_LOG_LEVELS, levels);
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    private List<Class<? extends IContext>> getContexts() {
        return StreamSupport.stream(ClassIndex.getSubclasses(IContext.class).spliterator(), false)
                .filter(aClass -> aClass != LogManagementService.class)
                .collect(Collectors.toList());
    }

    private void fillContext(List<Class<? extends IContext>> contexts) {
        injectLevelOption(root);
        for (Class<? extends IContext> context : contexts) {
            addContext(context);
        }
    }

    private ContextView addContext(Class<? extends IContext> contextClass) {
        Stream<ContextView> added = StreamSupport.stream(treeModel.spliterator(), false)
                .map(iNode -> (ContextView) iNode);
        return added.filter(ctx -> ctx.getContextClass() == contextClass).findFirst().orElseGet(() -> {
            ContextView parent  = addContext(contextClass.getAnnotation(IContext.Definition.class).parent());
            ContextView context = new ContextView(contextClass);
            injectLevelOption(context);
            parent.insert(context);
            return context;
        });
    }

    private void injectLevelOption(ContextView ctxView) {
        if (!ctxView.model.hasProperty(PROP_CTX_LEVEL)) {
            boolean isOption = ctxView.getContextClass().getAnnotation(LoggingSource.class).debugOption();
            ctxView.model.addDynamicProp(
                    PROP_CTX_LEVEL,
                    isOption ? new Bool(
                            getLevels().get(ctxView) == Level.Debug
                    ) : new Enum<>(
                            getLevels().get(ctxView)
                    ),
                    null, null
            );
            ctxView.model.addChangeListener((name, oldValue, newValue) -> {
                Level newLevel =
                      newValue instanceof Level ? (Level) newValue :
                      Boolean.TRUE.equals(newValue) ? Level.Debug : Level.Off;
                Map<ContextView, Level> levels = getLevels();
                levels.put(ctxView, newLevel);
                setLevels(levels);
            });
        }
    }
}
