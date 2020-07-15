package codex.log;

import codex.command.EditorCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.context.ContextView;
import codex.context.RootContext;
import codex.explorer.tree.NodeTreeModel;
import codex.model.*;
import codex.presentation.EditorPage;
import codex.presentation.SelectorTreeTable;
import codex.property.PropertyHolder;
import codex.service.Service;
import codex.type.*;
import codex.type.Enum;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.TreeModelEvent;
import java.awt.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@EntityDefinition(icon = "/images/lamp.png")
public final class LoggerServiceOptions extends Service<LogManagementService> {

    private final static ImageIcon LIMIT = ImageUtils.getByPath("/images/limit.png");
    final static Integer STORE_DAYS = 15;

    final static String PROP_DB_FILE = "dbFile";
    final static String PROP_DB_SIZE = "dbSize";
    final static String PROP_DB_DAYS = "storeDays";

    private final NodeTreeModel treeModel = new NodeTreeModel(new ContextView(Logger.getContextRegistry().getContext(RootContext.class)));

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
        EditorPage editorPage = getEditorPage();

        // Embedded context tree selector
        SelectorTreeTable<ContextView> treeTable = new SelectorTreeTable<>((ContextView) treeModel.getRoot(), ContextView.class);
        treeTable.setPropertyEditable(ContextView.PROP_LEVEL);

        treeModel.addTreeModelListener(new TreeModelAdapter() {
            @Override
            public void treeNodesInserted(TreeModelEvent event) {
                if (event.getTreePath().getPathCount() == 1) {
                    SwingUtilities.invokeLater(() -> treeTable.expandPath(event.getTreePath()));
                }
            }
        });

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
                changes.forEach(propName -> {
                    Logger.ContextInfo ctxInfo = Logger.getContextRegistry().getContext(propName);
                    if (ctxInfo != null) {
                        ContextView ctxView = attachContext(ctxInfo);
                        if (ctxView != null) {
                            Level prevLevel = (Level) model.getValue(propName);
                            ctxView.model.setValue(
                                    ContextView.PROP_LEVEL,
                                    Logger.isOption(ctxView.getContextClass()) ? prevLevel == Level.Debug : prevLevel
                            );
                        }
                    }
                });
            }

            @Override
            public void modelSaved(EntityModel model, List<String> changes) {
                changes.forEach(propName -> {
                    Logger.ContextInfo ctxInfo = Logger.getContextRegistry().getContext(propName);
                    if (ctxInfo != null) {
                        Logger.setContextLevel(ctxInfo.getClazz(), (Level) model.getValue(propName));
                    }
                });
            }
        });
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

    ContextView attachContext(Logger.ContextInfo contextInfo) {
        if (LogManagementService.class.equals(contextInfo.getClazz())) return null;

        Optional<ContextView> ctxView = StreamSupport.stream(treeModel.spliterator(), false)
                .map(iNode -> (ContextView) iNode)
                .filter(contextView -> contextView.getContextClass() == contextInfo.getClazz())
                .findFirst();
        if (ctxView.isPresent()) {
            return ctxView.get();
        } else {
            if (contextInfo.getParent() != null) {
                ContextView parent = attachContext(contextInfo.getParent());
                ContextView context = new ContextView(contextInfo);
                parent.attach(context);

                final String propName = contextInfo.getClazz().getTypeName();
                if (!model.hasProperty(propName)) {
                    model.addUserProp(propName, new Enum<>(contextInfo.getLevel()), false, Access.Select);
                }
                context.model.addChangeListener((name, oldValue, newValue) -> {
                    Level newLevel = newValue instanceof Level ? (Level) newValue : Boolean.TRUE.equals(newValue) ? Level.Debug : Level.Off;
                    model.setValue(propName, newLevel);
                });
                return context;
            }
        }
        return null;
    }

    void detachContext(Logger.ContextInfo contextInfo) {
        Optional<ContextView> ctxView = StreamSupport.stream(treeModel.spliterator(), false)
                .map(iNode -> (ContextView) iNode)
                .filter(contextView -> contextView.getContextClass() == contextInfo.getClazz())
                .findFirst();
        if (ctxView.isPresent()) {
            ContextView parent = attachContext(contextInfo.getParent());
            parent.detach(ctxView.get());
        }
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
                            } catch (Exception ignore) {}
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
