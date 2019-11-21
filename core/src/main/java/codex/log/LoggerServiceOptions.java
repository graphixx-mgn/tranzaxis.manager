package codex.log;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.context.ContextView;
import codex.context.IContext;
import codex.context.RootContext;
import codex.explorer.tree.INode;
import codex.explorer.tree.NodeTreeModel;
import codex.model.*;
import codex.presentation.EditorPage;
import codex.presentation.EditorPresentation;
import codex.presentation.SelectorTreeTable;
import codex.property.PropertyHolder;
import codex.service.Service;
import codex.type.*;
import codex.type.Enum;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@EntityDefinition(icon = "/images/lamp.png")
public final class LoggerServiceOptions extends Service<LogManagementService> {

    private final static ImageIcon LIMIT = ImageUtils.resize(ImageUtils.getByPath("/images/limit.png"), 20, 20);
    final static Integer STORE_DAYS = 15;

    final static String PROP_DB_FILE = "dbFile";
    final static String PROP_DB_SIZE = "dbSize";
    final static String PROP_DB_DAYS = "storeDays";

    private final ContextView   root = new ContextView(RootContext.class);
    private final NodeTreeModel treeModel = new NodeTreeModel(root);

    public LoggerServiceOptions(EntityRef owner, String title) {
        super(owner, title);

        // Properties
        model.addDynamicProp(
                PROP_DB_FILE, new Str(null), Access.Select,
                () -> getService() == null ? null : Paths.get(System.getProperty("user.home"), getService().getOption("file")).toString()
        );
        model.addDynamicProp(
                PROP_DB_SIZE, new Str(null), Access.Select,
                () -> getService() == null ? null : FileUtils.formatFileSize(
                        Paths.get(System.getProperty("user.home"), getService().getOption("file")).toFile().length()
                )
        );
		model.addUserProp(PROP_DB_DAYS, new Int(STORE_DAYS), false, Access.Any);

        // Editor settings
        model.getEditor(PROP_DB_SIZE).addCommand(new StorageLimit());

        // Build context tree
        fillContext(Logger.getContextRegistry().getContexts());

        // Embedded context tree selector
        SelectorTreeTable<ContextView> treeTable = new SelectorTreeTable<>((ContextView) treeModel.getRoot(), ContextView.class);
        treeTable.setPropertyEditable(ContextView.PROP_LEVEL);

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
                for (INode node : treeModel) {
                    final ContextView ctxView = (ContextView) node;
                    final Class<? extends IContext> contextClass = ctxView.getContextClass();
                    final String contextId = Logger.getContextRegistry().getContext(contextClass).getId();

                    Level prevLevel = (Level) model.getValue(contextId);
                    ctxView.model.setValue(
                            ContextView.PROP_LEVEL,
                            Logger.isOption(ctxView.getContextClass()) ? prevLevel == Level.Debug : prevLevel
                    );
                }
            }

            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                for (INode node : treeModel) {
                    final ContextView ctxView = (ContextView) node;
                    final Class<? extends IContext> contextClass = ctxView.getContextClass();

                    Object newValue = ctxView.model.getValue(ContextView.PROP_LEVEL);
                    Level  newLevel = newValue instanceof Level ? (Level) newValue :
                                    Boolean.TRUE.equals(newValue) ? Level.Debug : Level.Off;

                    Logger.setContextLevel(contextClass, newLevel);
                }
            }
        });

        // Listeners
        for (INode node : treeModel) {
            final ContextView ctxView = (ContextView) node;
            final Class<? extends IContext> contextClass = ctxView.getContextClass();
            final String propName = Logger.getContextRegistry().getContext(contextClass).getId();

            model.addUserProp(
                    propName,
                    new Enum<>(Logger.getContextRegistry().getContext(contextClass).getLevel()),
                    false,
                    Access.Select
            );

            ctxView.model.addChangeListener((name, oldValue, newValue) -> {
                Level newLevel =
                      newValue instanceof Level ? (Level) newValue :
                      Boolean.TRUE.equals(newValue) ? Level.Debug : Level.Off;
                model.setValue(propName, newLevel);
            });
        }
    }

    @Override
    public Class<? extends Entity> getChildClass() {
        return null;
    }

    @Override
    protected void onOpenPageView() {
        model.updateDynamicProps(PROP_DB_SIZE);
    }

    @Override
    protected void setService(LogManagementService service) {
        super.setService(service);
        model.updateDynamicProps(PROP_DB_FILE);
    }

    private void fillContext(Collection<Class<? extends IContext>> contexts) {
        for (Class<? extends IContext> context : contexts) {
            if (!ILogManagementService.class.isAssignableFrom(context)) {
                addContext(context);
            }
        }
    }

    private ContextView addContext(Class<? extends IContext> contextClass) {
        Stream<ContextView> added = StreamSupport.stream(treeModel.spliterator(), false)
                .map(iNode -> (ContextView) iNode);
        return added.filter(ctx -> ctx.getContextClass() == contextClass).findFirst().orElseGet(() -> {
            ContextView parent  = addContext(contextClass.getAnnotation(IContext.Definition.class).parent());
            ContextView context = new ContextView(contextClass);
            parent.insert(context);
            return context;
        });
    }

    private class StorageLimit extends EditorCommand<Str, String> {

        StorageLimit() {
            super(LIMIT, Language.get(LoggerServiceOptions.class, PROP_DB_DAYS+".title"), null);
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {
            ParamModel paramModel = new ParamModel();
            paramModel.addProperty(model.getProperty(PROP_DB_DAYS));

            DialogButton btnSubmit = Dialog.Default.BTN_OK.newInstance();
            DialogButton btnCancel = Dialog.Default.BTN_CANCEL.newInstance();

            codex.component.dialog.Dialog dialog = new codex.component.dialog.Dialog(
                    null,
                    ImageUtils.getByPath("/images/limit.png"),
                    Language.get(LoggerServiceOptions.class, "storage.limit@title"),
                    new JPanel(new BorderLayout()) {{
                        add(new EditorPage(paramModel), BorderLayout.NORTH);
                        setBorder(new CompoundBorder(
                                new EmptyBorder(10, 5, 5, 5),
                                new LineBorder(Color.LIGHT_GRAY, 1)
                        ));
                    }},
                    (event) -> {
                        if (event.getID() == Dialog.OK) {
                            try {
                                model.commit(true, PROP_DB_DAYS);
                            } catch (Exception ignore) {
                                ignore.printStackTrace();
                            }
                        } else {
                            model.rollback(PROP_DB_DAYS);
                        }
                    },
                    btnSubmit, btnCancel
            ) {
                @Override
                public Dimension getPreferredSize() {
                    Dimension prefSize = super.getPreferredSize();
                    return new Dimension(550, prefSize.getSize().height);
                }
            };
            dialog.setResizable(false);
            dialog.setVisible(true);
        }
    }
}
