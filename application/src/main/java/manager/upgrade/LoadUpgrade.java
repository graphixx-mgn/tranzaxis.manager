package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.component.ui.StripedProgressBarUI;
import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.instance.IInstanceDispatcher;
import codex.instance.Instance;
import codex.log.Level;
import codex.log.Logger;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.AnyType;
import codex.type.Iconified;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.Runtime;
import manager.upgrade.stream.RemoteInputStream;
import manager.utils.Versioning;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import org.apache.commons.io.FilenameUtils;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

class LoadUpgrade extends AbstractTask<Void> {

    private final static ImageIcon ICON_UPGRADE = ImageUtils.getByPath("/images/upgrade.png");
    private final static ImageIcon ICON_UNABLE  = ImageUtils.getByPath("/images/unavailable.png");
    private final static ImageIcon ICON_ENABLE  = ImageUtils.getByPath("/images/remotehost.png");
    private final static ImageIcon ICON_LOAD    = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );
    private final static String PARAM_VERSION   = "version";
    private final static String PARAM_PROVIDER  = "provider";

    private final String version;
    private final VersionsDocument diff;

//    private final Instance instance;
//    private final File     originalFile;
//    private final File     upgradedFile;

//    private final DialogButton restartBtn = Dialog.Default.BTN_OK.newInstance();
//    private final DialogButton closeBtn = Dialog.Default.BTN_CLOSE.newInstance();
//    private final Dialog dialog;

    static String fillStepResult(String step, String result, Throwable error) {
        StringBuilder builder = new StringBuilder(" &bull; ");
        builder.append(step);
        if (error == null) {
            if (result == null) {
                builder.append(String.join("", Collections.nCopies(69-step.length(), ".")));
                builder.append("<font color='green'>&#x2713;</font>");
            } else {
                int htmlLength = result.replaceAll("\\<[^>]*>","").length();
                builder.append(String.join("", Collections.nCopies(70-step.length()-htmlLength, ".")));
                builder.append(result);
            }
        } else {
            builder.append(String.join("", Collections.nCopies(69-step.length(), ".")));
            if (error.getMessage() != null) {
                builder.append(MessageFormat.format(
                        "<font color='red'>&#x26D4;</font><br/>   <font color='maroon'>{0}</font>",
                        error.getMessage()
                ));
            } else {
                builder.append("<font color='red'>&#x26D4;</font>");
            }
        }
        return builder.toString();
    }

    LoadUpgrade(String version, VersionsDocument diff) {
        super(Language.get(UpgradeUnit.class, "process@task"));
        this.version = version;
        this.diff = diff;

//        this.instance = instance;
//        this.originalFile = UpgradeService.getCurrentJar();
//        this.upgradedFile = new File(
//                originalFile.getParentFile(),
//                FilenameUtils.removeExtension(originalFile.getName()).concat("-upgrade.jar")
//        );
//        upgradedFile.deleteOnExit();
    }

    {
//        restartBtn.setText(Language.get(UpgradeUnit.class, "process@restart"));
//        restartBtn.setEnabled(false);
//        closeBtn.setEnabled(false);
//        dialog = new Dialog(
//                null,
//                ICON_UPGRADE,
//                Language.get(UpgradeUnit.class, "process@title"),
//                new JPanel(new BorderLayout()) {{
//                    setBorder(new EmptyBorder(5, 5, 5, 5));
//                    AbstractTaskView taskView = LoadUpgrade.this.createView(null);
//                    taskView.setBorder(new CompoundBorder(
//                            new LineBorder(Color.LIGHT_GRAY, 1),
//                            new EmptyBorder(5, 5, 5, 5)
//                    ));
//                    add(taskView, BorderLayout.NORTH);
//                    add(TaskOutput.createOutput(LoadUpgrade.this), BorderLayout.CENTER);
//                }},
//                (e) -> {
//                    if (e.getID() == Dialog.OK) {
//                        restart();
//                    }
//                },
//                restartBtn, closeBtn
//        );
//        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    private List<Instance> prepare() throws ExecutionException, InterruptedException {
        final ParamModel paramModel = new ParamModel();
        final PropertyHolder<AnyType, Object> propVersion = new PropertyHolder<>(
                PARAM_VERSION,
                Language.get(UpgradeUnit.class, "check@version"),
                null,
                new AnyType(MessageFormat.format(
                        "{0} &rarr; {1}",
                        UpgradeService.getVersion().getNumber(),
                        version
                )),
                false
        );
        final PropertyHolder<AnyType, Object> propProviders = new PropertyHolder<>(
                PARAM_PROVIDER,
                Language.get(UpgradeUnit.class, "check@providers"),
                null,
                new AnyType(null),
                false
        );

        paramModel.addProperty(propVersion);
        paramModel.addProperty(propProviders);
        ((AnyTypeView) paramModel.getEditor(PARAM_VERSION)).addCommand(
                new Versioning.ShowChanges(diff, Language.get(UpgradeUnit.class, "check@changes"))
        );

        final DialogButton submitBtn = Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class, "button@proceed"));
        final DialogButton cancelBtn = Dialog.Default.BTN_CANCEL.newInstance();
        submitBtn.setEnabled(false);

        JProgressBar progress = new JProgressBar() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 21);
            }
        };
        progress.setMaximum(100);
        progress.setUI(new StripedProgressBarUI(true));
        progress.setBorder(new EmptyBorder(3,3,3,3));
        progress.setStringPainted(true);

        Box propEditorComponent = paramModel.getEditor(PARAM_PROVIDER).getEditor();
        propEditorComponent.getComponent(0).setVisible(false);
        propEditorComponent.getComponent(1).setVisible(false);
        propEditorComponent.add(progress);

        final AbstractTask<List<Instance>> filterInstances = new AbstractTask<List<Instance>>(null) {
            @Override
            public List<Instance> execute() throws Exception {
                final List<Instance> initial = new LinkedList<>(
                        ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class).getInstances()
                );
                final List<Instance> result = new LinkedList<>();
                for (int idx = 0; idx < initial.size(); idx++) {
                    try {
                        final IUpgradeService upService = (IUpgradeService) initial.get(idx).getService(UpgradeService.class);
                        final Version availVersion = upService.getCurrentVersion();
                        if (availVersion.getNumber().equals(version)) {
                            result.add(initial.get(idx));
                        }
                        progress.setValue((idx+1) * 100 / initial.size());
                    } catch (Exception ignore) {}
                }
                return result;
            }

            @Override
            public void finished(List<Instance> result) {
                if (!result.isEmpty()) {
                    submitBtn.setEnabled(true);
                }
                propEditorComponent.remove(progress);
                propEditorComponent.getComponent(1).setVisible(true);

                paramModel.setValue(
                        PARAM_PROVIDER,
                        new Iconified() {
                            @Override
                            public ImageIcon getIcon() {
                                return result.isEmpty() ? ICON_UNABLE : ICON_ENABLE;
                            }

                            @Override
                            public String toString() {
                                return result.isEmpty() ?
                                        Language.get(UpgradeUnit.class, "provider@notfound") :
                                        MessageFormat.format(
                                                Language.get(UpgradeUnit.class, "provider@found"),
                                                String.valueOf(result.size())
                                        );
                            }
                        }
                );
            }
        };
        JPanel warnMessage = new JPanel(new BorderLayout()) {{
            JLabel label = new JLabel(
                    Language.get(UpgradeUnit.class, "check@warning"),
                    ImageUtils.getByPath("/images/warn.png"),
                    SwingConstants.LEFT
            );
            label.setOpaque(true);
            label.setIconTextGap(10);
            label.setBackground(new Color(0x33DE5347, true));
            label.setForeground(IEditor.COLOR_INVALID);
            label.setBorder(new CompoundBorder(
                    new LineBorder(Color.decode("#DE5347"), 1),
                    new EmptyBorder(5, 10, 5, 10)
            ));
            setBorder(new EmptyBorder(5, 10, 5, 10));
            add(label, BorderLayout.CENTER);
        }};

        Semaphore lock = new Semaphore(1);
        lock.acquire();
        final Dialog dialog = new Dialog(
                Dialog.findNearestWindow(),
                ICON_UPGRADE,
                Language.get(UpgradeUnit.class, "check@title"),
                new JPanel(new BorderLayout()) {{
                    add(warnMessage, BorderLayout.NORTH);
                    add(new EditorPage(paramModel), BorderLayout.CENTER);
                }},
                e -> {
                    if (e.getID() != Dialog.OK) {
                        if (!filterInstances.isDone()) {
                            filterInstances.cancel(true);
                        }
                        this.cancel(true);
                    }
                    lock.release();
                },
                submitBtn,
                cancelBtn
        ) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(500, super.getPreferredSize().height);
            }
        };
        dialog.setResizable(false);

        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(filterInstances);

        try {
            lock.acquire();
        } finally {
            lock.release();
        }
        return filterInstances.get();
    }

    @Override
    public Void execute() throws Exception {
        List<Instance> providers = prepare();

        final DialogButton dialogButton = Dialog.Default.BTN_OK.newInstance();
        dialogButton.setText(Language.get(UpgradeUnit.class, "process@restart"));
        dialogButton.setEnabled(false);

        final File originalFile = Runtime.APP.jarFile.get();
        final File upgradedFile = new File(
                originalFile.getParentFile(),
                FilenameUtils.removeExtension(originalFile.getName()).concat("-upgrade.jar")
        );

        Dialog dialog = new Dialog(
                Dialog.findNearestWindow(),
                ICON_LOAD,
                Language.get(UpgradeUnit.class, "process@title"),
                new JPanel(new BorderLayout(0, 5)) {{
                    setBorder(new EmptyBorder(5, 5, 5, 5));
                    AbstractTaskView taskView = LoadUpgrade.this.createView(null);
                    taskView.setBorder(new CompoundBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1),
                            new EmptyBorder(5, 5, 5, 5)
                    ));
                    add(taskView, BorderLayout.NORTH);
                    add(TaskOutput.createOutput(LoadUpgrade.this), BorderLayout.CENTER);
                }},
                e -> {
                    if (isDone() && !isFailed()) {
                        reload(originalFile, upgradedFile);
                    }
                },
                dialogButton
        );

        SwingUtilities.invokeLater(() -> {
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setPreferredSize(new Dimension(560, 400));
            dialog.setResizable(false);
            dialog.setVisible(true);
        });

        Thread.sleep(100);

        List<Version> chain = new LinkedList<>(Arrays.asList(diff.getVersions().getVersionArray()));
        chain.add(0, UpgradeService.getVersion());
        TaskOutput.put(
                Level.Debug,
                Language.get(UpgradeUnit.class, "process@sequence"),
                chain.stream()
                        .map(Version::getNumber)
                        .collect(Collectors.joining(" => "))
        );

        String remoteChecksum;
        final MessageDigest localChecksum = MessageDigest.getInstance("MD5");

        for (Instance provider : providers) {
            String step = null;
            try {
                step = Language.get(UpgradeUnit.class, "process@connect");
                IUpgradeService remoteUpService = (IUpgradeService) provider.getService(UpgradeService.class);
                TaskOutput.put(
                        Level.Debug,
                        fillStepResult(step, MessageFormat.format(
                                "<font color='green'>{0}{1}</font>",
                                provider.getUser(),
                                provider.getRemoteAddress()
                        ), null)
                );
                remoteChecksum = remoteUpService.getUpgradeFileChecksum();

                step = Language.get(UpgradeUnit.class, "process@stream");
                try (
                    FileOutputStream  outStream = new FileOutputStream(upgradedFile);
                    RemoteInputStream inStream = remoteUpService.getUpgradeFileStream()
                ) {
                    long fileSize = inStream.available();
                    TaskOutput.put(
                            Level.Debug,
                            fillStepResult(step, MessageFormat.format(
                                    "<font color='green'>{0}/{1}</font>",
                                    FileUtils.formatFileSize(fileSize), remoteChecksum
                            ), null)
                    );

                    step = Language.get(UpgradeUnit.class, "process@load");
                    try {
                        byte[] data = new byte[1024];
                        long totalRead = 0;
                        int bytesRead = inStream.read(data);
                        while (bytesRead != -1) {
                            totalRead = totalRead + bytesRead;
                            outStream.write(data, 0, bytesRead);
                            localChecksum.update(data, 0, bytesRead);
                            setProgress((int) (100 * totalRead / fileSize), getDescription());
                            bytesRead = inStream.read(data);
                        }
                        TaskOutput.put(
                                Level.Debug,
                                fillStepResult(step, null, null)
                        );
                    } catch (IOException e) {
                        throw new Error(Language.get(UpgradeUnit.class, "process@transmission.error"));
                    }

                    step = Language.get(UpgradeUnit.class, "process@checksum");
                    if (DatatypeConverter.printHexBinary(localChecksum.digest()).equals(remoteChecksum)) {
                        TaskOutput.put(
                                Level.Debug,
                                fillStepResult(step, null, null)
                        );
                    } else {
                        throw new Error(Language.get(UpgradeUnit.class, "process@check.error"));
                    }

                    dialogButton.setEnabled(true);
                    return null;
                }
            } catch (Throwable e) {
                TaskOutput.put(Level.Debug, fillStepResult(step,null, e));
                if (upgradedFile.exists() && !upgradedFile.delete()) {
                    upgradedFile.deleteOnExit();
                }
            }
        }

        dialogButton.setEnabled(true);
        dialogButton.setText(Language.get(Dialog.class, "close@title"));
        dialogButton.setIcon(ImageUtils.getByPath("/images/close.png"));
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        throw new ExecuteException(
                Language.get(UpgradeUnit.class, "process@terminate"),
                Language.get(UpgradeUnit.class, "process@terminate")
        );
    }

    @Override
    public void finished(Void result) {}

    private void reload(File original, File upgrade) {
        final ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(original.getAbsolutePath());
        final ProcessBuilder builder = new ProcessBuilder(command);

        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (
                FileChannel src  = new FileInputStream(upgrade).getChannel();
                FileChannel dest = new FileOutputStream(original).getChannel()
            ) {
                dest.transferFrom(src, 0, src.size());
                if (upgrade.exists() && !upgrade.delete()) {
                    upgrade.deleteOnExit();
                }
                builder.start();
            } catch (IOException e) {
                Logger.getLogger().error("Error", e);
            }
        }));
        System.exit(0);
    }
}
