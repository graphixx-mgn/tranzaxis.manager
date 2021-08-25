package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.editor.AnyTypeView;
import codex.editor.IEditor;
import codex.instance.IInstanceDispatcher;
import codex.instance.Instance;
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
import manager.xml.VersionsDocument;
import org.apache.commons.io.FilenameUtils;
import org.bridj.util.Pair;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class LoadUpgrade extends AbstractTask<Void> {

    private final static ITaskExecutorService TES = ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class);

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

    private final static JPanel WARN_MESSAGE = new JPanel(new BorderLayout()) {{
        JLabel label = new JLabel(
                Language.get(UpgradeUnit.class, "check@warning"),
                ImageUtils.getByPath("/images/warn.png"),
                SwingConstants.LEFT
        );
        label.setOpaque(true);
        label.setIconTextGap(5);
        label.setBackground(new Color(0x33DE5347, true));
        label.setForeground(IEditor.COLOR_INVALID);
        label.setBorder(new CompoundBorder(
                new LineBorder(Color.decode("#DE5347"), 1),
                new EmptyBorder(5, 5, 5, 5)
        ));
        setBorder(new EmptyBorder(0, 0, 5, 0));
        add(label, BorderLayout.CENTER);
    }};

    private final String version;
    private final VersionsDocument diff;

    LoadUpgrade(String version, VersionsDocument diff) {
        super(Language.get(UpgradeUnit.class, "process@task"));
        this.version = version;
        this.diff = diff;
    }

    private Iconified getResultView(Integer found) {
        return new Iconified() {
            @Override
            public ImageIcon getIcon() {
                return found == null ? ImageUtils.grayscale(ICON_ENABLE) : (found == 0 ? ICON_UNABLE : ICON_ENABLE);
            }

            @Override
            public String toString() {
                return  found == null ? Language.get(UpgradeUnit.class, "provider@checking") : (
                        found == 0 ?
                            Language.get(UpgradeUnit.class, "provider@notfound") :
                            MessageFormat.format(
                                    Language.get(UpgradeUnit.class, "provider@found"),
                                    String.valueOf(found)
                            )
                );
            }
        };
    }

    private List<Instance> prepare() throws ExecutionException, InterruptedException {
        final DialogButton submitBtn = Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class, "button@proceed"));
        final DialogButton cancelBtn = Dialog.Default.BTN_CANCEL.newInstance();
        submitBtn.setEnabled(false);

        // Parameters
        final PropertyHolder<AnyType, Object> propVersion = new PropertyHolder<>(
                PARAM_VERSION,
                Language.get(UpgradeUnit.class, "check@version"),
                null,
                new AnyType(MessageFormat.format(
                        "{0} &rarr; {1}",
                        UpgradeService.getReleaseVersion().getNumber(),
                        version
                )),
                false
        );
        final PropertyHolder<AnyType, Object> propProviders = new PropertyHolder<>(
                PARAM_PROVIDER,
                Language.get(UpgradeUnit.class, "check@providers"),
                null,
                new AnyType(getResultView(null)),
                false
        );
        final ParamModel paramModel = new ParamModel() {{
            addProperty(propVersion);
            addProperty(propProviders);
            ((AnyTypeView) getEditor(PARAM_VERSION)).addCommand(
                    new Versioning.ShowChanges(diff, Language.get(UpgradeUnit.class, "check@changes"))
            );
        }};
        final EditorPage paramPage = new EditorPage(paramModel);
        paramPage.setBorder(new CompoundBorder(
                new EmptyBorder(5, 0, 0, 0),
                new LineBorder(Color.LIGHT_GRAY, 1)
        ));

        // Filter instances task
        final GetProviders task = new GetProviders(version) {
            @Override
            public void finished(List<Instance> result) {
                super.finished(result);
                SwingUtilities.invokeLater(() -> {
                    submitBtn.setEnabled(!result.isEmpty());
                    paramModel.setValue(PARAM_PROVIDER, getResultView(result.size()));
                });
            }
        };
        final AbstractTaskView taskView = task.createView(null);
        taskView.setBorder(new CompoundBorder(
                new LineBorder(Color.LIGHT_GRAY, 1),
                new EmptyBorder(5, 5, 5, 5)
        ));
        final Semaphore lock = new Semaphore(1);
        try {
            lock.acquire();
            final Dialog dialog = new Dialog(
                    Dialog.findNearestWindow(),
                    ICON_UPGRADE,
                    Language.get(UpgradeUnit.class, "check@title"),
                    new JPanel(new BorderLayout()) {{
                        setBorder(new EmptyBorder(5, 10, 5, 10));
                        add(WARN_MESSAGE, BorderLayout.NORTH);
                        add(paramPage, BorderLayout.SOUTH);
                        add(taskView, BorderLayout.CENTER);
                    }},
                    e -> {
                        if (e.getID() != Dialog.OK) {
                            task.cancel(true);
                        }
                        lock.release();
                    },
                    submitBtn,
                    cancelBtn
            ) {
                {
                    setResizable(false);
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(500, super.getPreferredSize().height);
                }
            };
            TES.quietTask(task);
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
            lock.acquire();
            if (dialog.getExitCode() != Dialog.OK) {
                throw new CancelException();
            }
            return task.get();
        } finally {
            lock.release();
        }
    }

    @Override
    public Void execute() throws Exception {
        List<Instance> providers = prepare();
        TES.quietTask(new DownloadUpgrade(providers));
        return null;
    }

    @Override
    public void finished(Void result) {}


    private static class GetProviders extends AbstractTask<List<Instance>> {

        private final String version;

        private GetProviders(String version) {
            super(Language.get(UpgradeUnit.class, "check@instances"));
            this.version  = version;
        }

        @Override
        public List<Instance> execute() throws Exception {
            List<Instance> instances = new LinkedList<>(
                    ServiceRegistry.getInstance().lookupService(IInstanceDispatcher.class).getInstances()
            );
            AtomicInteger checked = new AtomicInteger(0);
            return instances.stream()
                    .filter(instance -> {
                        try {
                            setProgress(getProgress(), instance.toString());
                            final IUpgradeService upService = (IUpgradeService) instance.getService(UpgradeService.class);
                            return upService.getCurrentVersion().getNumber().equals(version);
                        } catch (Exception e) {
                            return false;
                        } finally {
                            setProgress(
                                    checked.addAndGet(1) * 100 / instances.size(),
                                    getDescription()
                            );
                        }
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public void finished(List<Instance> result) {}
    }


    private static class DownloadUpgrade extends AbstractTask<File> {

        private final List<Instance> providers;

        private DownloadUpgrade(List<Instance> providers) {
            super(Language.get(UpgradeUnit.class, "process@task"));
            this.providers = providers;
        }

        @Override
        public File execute() throws Exception {
            final File originalFile = Runtime.APP.jarFile.get();
            final File upgradedFile = new File(
                    originalFile.getParentFile(),
                    FilenameUtils.removeExtension(originalFile.getName()).concat("-upgrade.jar")
            );

            final DialogButton dialogButton = Dialog.Default.BTN_OK.newInstance(Language.get(UpgradeUnit.class, "process@restart"));
            dialogButton.setEnabled(false);
            Dialog dialog = new Dialog(
                    Dialog.findNearestWindow(),
                    ICON_LOAD,
                    Language.get(UpgradeUnit.class, "process@title"),
                    new JPanel(new BorderLayout(0, 5)) {{
                        setBorder(new EmptyBorder(5, 5, 5, 5));
                        AbstractTaskView taskView = createView(null);
                        taskView.setBorder(new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                new EmptyBorder(5, 5, 5, 5)
                        ));
                        add(taskView, BorderLayout.NORTH);
                        add(TaskOutput.createOutput(DownloadUpgrade.this), BorderLayout.CENTER);
                    }},
                    e -> {
                        if (isDone() && !isFailed()) {
                            Logger.getContextLogger(UpgradeService.class).info("Reload file: {0}", originalFile);
                            reload(originalFile, upgradedFile);
                        } else {
                            if (upgradedFile.exists() && !upgradedFile.delete()) {
                                upgradedFile.deleteOnExit();
                            }
                        }
                    },
                    dialogButton
            ) {{
                setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                setPreferredSize(new Dimension(560, 400));
                setResizable(false);
            }};
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));

            for (Instance provider : providers) {
                try {
                    IUpgradeService remoteUpService = new ConnectInstance(provider).process();
                    Pair<RemoteInputStream, String> streamData = new CreateRemoteStream(remoteUpService).process();
                    try (
                            FileOutputStream outputStream = new FileOutputStream(upgradedFile);
                            RemoteInputStream inputStream = streamData.getKey();
                    ) {
                        String remoteCRC = streamData.getValue();
                        String localCRC  = new LoadFile(inputStream, outputStream, percent -> setProgress(percent, getDescription())).process();
                        if (new CheckCRC(remoteCRC, localCRC).process()) {
                            dialogButton.setEnabled(true);
                            return upgradedFile;
                        }
                    }
                    break;
                } catch (Exception e) {
                    //
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
        public void finished(File result) {}

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


    private static class ConnectInstance extends TaskOutput.ExecPhase<IUpgradeService> {

        private final Instance provider;
        ConnectInstance(Instance provider) {
            super(Language.get(UpgradeUnit.class, "process@connect"));
            this.provider = provider;
        }

        @Override
        protected Pair<String, IUpgradeService> execute() throws Exception {
            IUpgradeService remoteUpService = (IUpgradeService) provider.getService(UpgradeService.class);
            return new Pair<>(MessageFormat.format(
                    "<font color='green'>{0}{1}</font>",
                    provider.getUser(),
                    provider.getRemoteAddress()
            ), remoteUpService);
        }
    }


    private static class CreateRemoteStream extends TaskOutput.ExecPhase<Pair<RemoteInputStream, String>> {

        private final IUpgradeService service;
        CreateRemoteStream(IUpgradeService service) {
            super(Language.get(UpgradeUnit.class, "process@stream"));
            this.service = service;
        }

        @Override
        protected Pair<String, Pair<RemoteInputStream, String>> execute() throws Exception {
            String          remoteChecksum = service.getUpgradeFileChecksum();
            RemoteInputStream remoteStream = service.getUpgradeFileStream();
            return new Pair<>(
                    MessageFormat.format(
                        "<font color='green'>{0}/{1}</font>",
                        FileUtils.formatFileSize(remoteStream.available()), remoteChecksum
                    ),
                    new Pair<>(remoteStream, remoteChecksum)
            );
        }
    }


    private static class LoadFile extends TaskOutput.ExecPhase<String> {

        private final InputStream  inputStream;
        private final OutputStream outputStream;
        private final Consumer<Integer> progress;
        LoadFile(InputStream inputStream, OutputStream outputStream, Consumer<Integer> progress) {
            super(Language.get(UpgradeUnit.class, "process@load"));
            this.inputStream  = inputStream;
            this.outputStream = outputStream;
            this.progress     = progress;
        }

        @Override
        protected Pair<String, String> execute() throws Exception {
            long  fileSize = inputStream.available();
            final MessageDigest localChecksum = MessageDigest.getInstance("MD5");
            try {
                byte[] data = new byte[1024];
                long totalRead = 0;
                int bytesRead = inputStream.read(data);
                while (bytesRead != -1) {
                    totalRead = totalRead + bytesRead;
                    outputStream.write(data, 0, bytesRead);
                    localChecksum.update(data, 0, bytesRead);
                    progress.accept((int) (100 * totalRead / fileSize));
                    bytesRead = inputStream.read(data);
                }
                return new Pair<>(null, DatatypeConverter.printHexBinary(localChecksum.digest()));
            } catch (IOException e) {
                throw new IOException(Language.get(UpgradeUnit.class, "process@transmission.error"));
            }
        }
    }


    private static class CheckCRC extends TaskOutput.ExecPhase<Boolean> {

        private final String remote, local;
        CheckCRC(String remote, String local) {
            super(Language.get(UpgradeUnit.class, "process@checksum"));
            this.remote = remote;
            this.local  = local;
        }

        @Override
        protected Pair<String, Boolean> execute() throws Exception {
            if (remote.equals(local)) {
                return new Pair<>(null, true);
            } else {
                throw new Error(Language.get(UpgradeUnit.class, "process@check.error"));
            }
        }
    }
}
