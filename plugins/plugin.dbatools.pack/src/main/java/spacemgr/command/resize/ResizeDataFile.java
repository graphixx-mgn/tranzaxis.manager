package spacemgr.command.resize;

import codex.command.EditorCommand;
import codex.command.EntityCommand;
import codex.component.dialog.Dialog;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.editor.AnyTypeView;
import codex.editor.EntityRefEditor;
import codex.editor.IEditorFactory;
import codex.editor.StrEditor;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.type.*;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import spacemgr.command.objects.DataFile;
import spacemgr.command.objects.TableSpace;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ResizeDataFile extends EntityCommand<TableSpace> {

    private final static String PARAM_DATA_FILE = "file";
    private final static String PARAM_FILE_WARN = "warn";
    private final static String PARAM_CURR_TEXT = "currText";
    private final static String PARAM_MIN_LONG  = "minLong";
    private final static String PARAM_MIN_TEXT  = "minText";
    private final static String PARAM_NEW_SIZE  = "newSize";

    public ResizeDataFile() {
        super(
                "resize datafile",
                Language.get("title"),
                ImageUtils.getByPath("/images/resize.png"),
                Language.get("title"),
                tableSpace -> tableSpace.getBlockSize() != 0
        );
        setParameters(
                new PropertyHolder<>(PARAM_DATA_FILE, new EntityRef<DataFile>(DataFile.class) {
                    @Override
                    public IEditorFactory<EntityRef<DataFile>, DataFile> editorFactory() {
                        return propHolder -> new EntityRefEditor<DataFile>(propHolder) {
                            @Override
                            protected List<DataFile> getValues() {
                                TableSpace tbs = getContext().get(0);
                                return tbs.getDatafiles();
                            }
                        };
                    }
                }, true),
                new PropertyHolder<>(PARAM_FILE_WARN, new AnyType(new Iconified() {
                    @Override
                    public ImageIcon getIcon() {
                        return ImageUtils.getByPath("/images/warn.png");
                    }

                    @Override
                    public String toString() {
                        return Language.get(ResizeDataFile.class, "warn.message");
                    }
                }) {
                    @Override
                    public IEditorFactory<AnyType, Object> editorFactory() {
                        return propHolder -> new AnyTypeView(propHolder) {{
                            setBorder(new LineBorder(Color.RED, 1));
                        }};
                    }
                }, false),
                new PropertyHolder<>(PARAM_CURR_TEXT, new Str(null), true),
                new PropertyHolder<>(PARAM_MIN_LONG,  new BigInt(null), true),
                new PropertyHolder<>(PARAM_MIN_TEXT,  new Str(null), true),
                new PropertyHolder<>(PARAM_NEW_SIZE,  new Int(null), true)
        );
    }

    @Override
    protected void preprocessParameters(ParamModel paramModel) {
        super.preprocessParameters(paramModel);
        TableSpace tbs = getContext().get(0);
        tbs.coalesce();

        // Handlers
        paramModel.getProperty(PARAM_DATA_FILE).addChangeListener((name, oldValue, newValue) -> {
            DataFile dataFile = (DataFile) newValue;
            paramModel.getEditor(PARAM_FILE_WARN).setVisible(dataFile != null && !dataFile.isAutoExtensible());
            Dialog.findNearestWindow().pack();

            paramModel.setValue(PARAM_MIN_LONG,  dataFile == null ? null : dataFile.getMinimalSize());
            paramModel.setValue(PARAM_CURR_TEXT, dataFile == null ? null : FileUtils.formatFileSize(
                    dataFile.getBlocks() * tbs.getBlockSize(),
                    FileUtils.Dimension.MB
            ));
        });
        paramModel.getProperty(PARAM_MIN_LONG).addChangeListener((name, oldValue, newValue) -> paramModel.setValue(
                PARAM_MIN_TEXT,
                newValue == null ? null : FileUtils.formatFileSize((Long) newValue, FileUtils.Dimension.MB)
        ));
        ((Int) paramModel.getProperty(PARAM_NEW_SIZE).getPropValue()).setMask(value -> value >= DataFile.truncateSize((Long) paramModel.getValue(PARAM_MIN_LONG)));

        // Initiate values
        paramModel.setValue(PARAM_NEW_SIZE, null);
        paramModel.setValue(PARAM_DATA_FILE, null);
        paramModel.setValue(PARAM_DATA_FILE, tbs.getDatafiles().get(0));

        // Editor settings
        paramModel.getEditor(PARAM_FILE_WARN).setVisible(false);
        paramModel.getEditor(PARAM_DATA_FILE).setEditable(tbs.getDatafiles().size() > 1);
        paramModel.getEditor(PARAM_CURR_TEXT).setEditable(false);
        paramModel.getEditor(PARAM_MIN_LONG).setVisible(false);
        paramModel.getEditor(PARAM_MIN_TEXT).setEditable(false);

        // Editor commands
        CopyValue minSizeSetter = new CopyValue() {
            @Override
            public void execute(PropertyHolder<Str, String> context) {
                paramModel.setValue(PARAM_NEW_SIZE, DataFile.truncateSize((Long) paramModel.getValue(PARAM_MIN_LONG)));
            }
        };
        ((StrEditor) paramModel.getEditor(PARAM_MIN_TEXT)).addCommand(minSizeSetter);
    }

    @Override
    public boolean multiContextAllowed() {
        return false;
    }

    @Override
    public void execute(TableSpace context, Map<String, IComplexType> params) {
        DataFile file = (DataFile) params.get(PARAM_DATA_FILE).getValue();
        try {
            file.resize((Integer) params.get(PARAM_NEW_SIZE).getValue());
            SwingUtilities.invokeLater(() -> MessageBox.show(
                    MessageType.INFORMATION,
                    Language.get(ResizeDataFile.class, "result@success")
            ));
            context.updateInfo();
        } catch (SQLException e) {
            MessageBox.show(MessageType.ERROR, e.getMessage());
        }
    }

    private class CopyValue extends EditorCommand<Str, String> {

        private CopyValue() {
            super(
                    ImageUtils.getByPath("/images/paste.png"),
                    Language.get(ResizeDataFile.class, "command.copy@title"),
                    holder -> !holder.isEmpty()
            );
        }

        @Override
        public boolean disableWithContext() {
            return false;
        }

        @Override
        public Direction commandDirection() {
            return Direction.Consumer;
        }

        @Override
        public void execute(PropertyHolder<Str, String> context) {}
    }
}
