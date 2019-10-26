package plugin;

import codex.command.EntityGroupCommand;
import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.instance.Instance;
import codex.log.Level;
import codex.model.Entity;
import codex.service.ServiceRegistry;
import codex.task.*;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.stream.RemoteInputStream;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class DownloadPackages extends EntityGroupCommand<RemotePackageView> {

    private final static String STEP_DOWNLOAD = Language.get("step@load");
    private final static String STEP_INSTALL  = Language.get("step@install");
    private final static String STEP_PUBLISH  = Language.get("step@publish");

    private final static String STEP_SUCCESS  = Language.get("step@success");
    private final static String STEP_FAILED   = Language.get("step@fail");

    private final static ImageIcon ICON_LOAD = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );

    public DownloadPackages() {
        super(
                "load plugins",
                Language.get("title"),
                ICON_LOAD,
                Language.get("title"),
                null
        );
    }

    @Override
    public void execute(List<RemotePackageView> context, Map<String, IComplexType> params) {
        ServiceRegistry.getInstance().lookupService(ITaskExecutorService.class).quietTask(
                new TaskLoadUpdates(context.stream().map(packageView -> packageView.remotePackage).collect(Collectors.toList()))
        );
    }


    final class TaskLoadUpdates extends AbstractTask<Void> {

        private final List<IPluginLoaderService.RemotePackage> packages;
        private final DialogButton closeBtn = Dialog.Default.BTN_CLOSE.newInstance();

        TaskLoadUpdates(List<IPluginLoaderService.RemotePackage> packages) {
            super(Language.get(DownloadPackages.class, "task@title"));
            this.packages = packages;
            closeBtn.setEnabled(false);
        }

        private String fillStepResult(String step, String result, Throwable error) {
            int htmlLength = result.replaceAll("\\<[^>]*>","").length();
            return " * "
                    .concat(step)
                    .concat(String.join("", Collections.nCopies(70-step.length()-htmlLength, ".")))
                    .concat(result)
                    .concat(error == null ? "" : MessageFormat.format("\n   &#9888; {0}", error.getMessage()));
        }

        @Override
        public Void execute() throws Exception {
            Dialog dialog = new Dialog(
                    null,
                    ICON_LOAD,
                    Language.get(DownloadPackages.class, "dialog@title"),
                    new JPanel(new BorderLayout(0, 5)) {{
                        setBorder(new EmptyBorder(5, 5, 5, 5));
                        AbstractTaskView taskView = TaskLoadUpdates.this.createView(null);
                        taskView.setBorder(new CompoundBorder(
                                new LineBorder(Color.LIGHT_GRAY, 1),
                                new EmptyBorder(5, 5, 5, 5)
                        ));
                        add(taskView, BorderLayout.NORTH);
                        add(TaskOutput.createOutput(TaskLoadUpdates.this), BorderLayout.CENTER);
                    }},
                    e -> {
                        if (e.getID() == Dialog.EXIT) {
                            TaskLoadUpdates.this.cancel(true);
                        }
                    },
                    closeBtn
            );
            SwingUtilities.invokeLater(() -> {
                dialog.setPreferredSize(new Dimension(560, 400));
                dialog.setResizable(false);
                dialog.setVisible(true);
            });
            for (IPluginLoaderService.RemotePackage remotePackage : packages) {
                setProgress(
                        100*(packages.indexOf(remotePackage)) / packages.size(),
                        MessageFormat.format(Language.get(DownloadPackages.class, "task@progress"), remotePackage.toString())
                );
                if (remotePackage.isAvailable()) {
                    TaskOutput.put(
                            Level.Debug,
                            Language.get(DownloadPackages.class, "task@package.start"),
                            remotePackage.getId(),
                            remotePackage.getVersion(),
                            remotePackage.getInstances().size()
                    );
                    for (Instance provider : remotePackage.getInstances()) {
                        try {
                            TaskOutput.put(
                                    Level.Debug,
                                    fillStepResult(
                                            Language.get(DownloadPackages.class, "task@package.provider"),
                                            provider.getUser() + provider.getRemoteAddress(),
                                            null
                                    )
                            );
                            loadPackageFile(
                                    (IPluginLoaderService) provider.getService(PluginLoaderService.class),
                                    remotePackage
                            );
                            break;
                        } catch (Exception e) {
                            //
                        }
                    }
                } else {
                    TaskOutput.put(
                            Level.Debug,
                            Language.get(DownloadPackages.class, "task@package.skip"),
                            remotePackage.getId(),
                            remotePackage.getVersion()
                    );
                }
            }
            setProgress(100, Status.FINISHED.getDescription());
            return null;
        }

        private void loadPackageFile(IPluginLoaderService pluginLoader, IPluginLoaderService.RemotePackage remotePackage) throws Exception {
            MessageDigest localChecksum = MessageDigest.getInstance("MD5");
            String remoteChecksum = pluginLoader.getPackageFileChecksum(remotePackage.getId(), remotePackage.getVersion());

            File upgradeFile = new File(
                    PluginManager.PLUGIN_DIR,
                    MessageFormat.format("{0}-{1}.jar", remotePackage.getTitle(), remotePackage.getVersion())
            );

            try (
                    FileOutputStream outStream = new FileOutputStream(upgradeFile)
            ) {
                RemoteInputStream inStream = pluginLoader.getPackageFileStream(remotePackage.getId(), remotePackage.getVersion());
                byte[] data = new byte[1024];
                long totalRead = 0;
                int  bytesRead = inStream.read(data);
                while (bytesRead != -1) {
                    totalRead = totalRead + bytesRead;
                    outStream.write(data, 0, bytesRead);
                    localChecksum.update(data, 0, bytesRead);
                    bytesRead = inStream.read(data);
                }
                try {
                    inStream.close();
                } catch (IOException e) {
                    // Do nothing
                }
                if (DatatypeConverter.printHexBinary(localChecksum.digest()).equals(remoteChecksum)) {
                    TaskOutput.put(
                            Level.Debug,
                            fillStepResult(STEP_DOWNLOAD, STEP_SUCCESS, null)
                    );
                    try {
                        installPackageFile(upgradeFile);
                    } catch (Exception e) {
                        if (!upgradeFile.delete()) {
                            upgradeFile.deleteOnExit();
                        }
                    }
                } else {
                    throw new Exception(Language.get(DownloadPackages.class, "error@checksum"));
                }
            } catch (Exception e) {
                TaskOutput.put(
                        Level.Warn,
                        fillStepResult(STEP_DOWNLOAD, STEP_FAILED, e)
                );
                Files.delete(upgradeFile.toPath());
                throw e;
            }
        }

        private void installPackageFile(File jarFile) throws Exception {
            PluginPackage newPackage;
            try {
                newPackage = new PluginPackage(jarFile);
            } catch (IOException e) {
                TaskOutput.put(
                        Level.Debug,
                        fillStepResult(STEP_INSTALL, STEP_FAILED, new Error(Language.get(DownloadPackages.class, "error@metainfo")))
                );
                throw e;
            }
            PluginPackage oldPackage = PluginManager.getInstance().getPluginLoader().getPackageById(newPackage.getId());
            boolean needPublish = oldPackage == null || Entity.newInstance(PackageView.class, null, oldPackage.getTitle()).isPublished();

            try {
                if (oldPackage != null) {
                    PackageView oldView = Entity.newInstance(PackageView.class, null, oldPackage.getTitle());
                    PluginManager.getInstance().getPluginLoader().removePluginPackage(oldPackage, true);
                    Entity.deleteInstance(oldView, false, false);
                }
                PluginManager.getInstance().getPluginLoader().addPluginPackage(newPackage);
                TaskOutput.put(
                        Level.Debug,
                        fillStepResult(STEP_INSTALL, STEP_SUCCESS, null)
                );
            } catch (PluginException | IOException e) {
                if (e instanceof FileSystemException) {
                    TaskOutput.put(
                            Level.Warn,
                            fillStepResult(STEP_INSTALL, STEP_FAILED, new Error(((FileSystemException) e).getReason()))
                    );
                } else {
                    TaskOutput.put(
                            Level.Warn,
                            fillStepResult(STEP_INSTALL, STEP_FAILED, e)
                    );
                }
                newPackage.close();
                throw e;
            }

            if (needPublish) {
                PackageView packageView = Entity.newInstance(PackageView.class, null, newPackage.getTitle());
                try {
                    packageView.setPublished(true);
                    TaskOutput.put(
                            Level.Debug,
                            fillStepResult(STEP_PUBLISH, STEP_SUCCESS, null)
                    );
                } catch (Exception e) {
                    TaskOutput.put(
                            Level.Debug,
                            fillStepResult(STEP_PUBLISH, STEP_FAILED, e)
                    );
                }
            }
        }

        @Override
        public void finished(Void result) {
            closeBtn.setEnabled(true);
        }
    }
}
