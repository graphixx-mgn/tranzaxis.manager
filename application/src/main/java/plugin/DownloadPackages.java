package plugin;

import codex.command.EntityGroupCommand;
import codex.component.dialog.Dialog;
import codex.editor.IEditor;
import codex.instance.Instance;
import codex.log.Level;
import codex.log.Logger;
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
import java.nio.file.Files;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class DownloadPackages extends EntityGroupCommand<RemotePackageView> {

    private final static String STEP_DOWNLOAD = Language.get("step@load");
    private final static String STEP_CHECKSUM = Language.get("step@check");
    private final static String STEP_INSTALL  = Language.get("step@install");
            final static String STEP_RELOAD   = Language.get("step@reload");
            final static String STEP_REMOVE   = Language.get("step@remove");
    private final static String STEP_PUBLISH  = Language.get("step@publish");

    private final static ImageIcon ICON_LOAD = ImageUtils.combine(
            ImageUtils.getByPath("/images/repository.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/down.png"), 20, 20),
            SwingConstants.SOUTH_EAST
    );

    static boolean installPackage(IPluginLoaderService.RemotePackage remotePackage) {
        for (Instance provider : remotePackage.getInstances()) {
            TaskOutput.put(
                    Level.Debug,
                    fillStepResult(
                            Language.get(DownloadPackages.class, "task@package.provider"),
                            provider.getUser() + provider.getRemoteAddress(),
                            null
                    )
            );
            try {
                IPluginLoaderService pluginLoader = (IPluginLoaderService) provider.getService(PluginLoaderService.class);
                File loadedFile = loadPackageFile(pluginLoader, remotePackage);
                if (loadedFile != null) {
                    try {
                        installPackageFile(loadedFile);
                        TaskOutput.put(Level.Debug, fillStepResult(STEP_INSTALL, null, null));
                    } catch (Exception e) {
                        Logger.getLogger().warn("Unable to install the update", e);
                        TaskOutput.put(
                                Level.Warn,
                                fillStepResult(STEP_INSTALL, null, new Error(Language.get(DownloadPackages.class, "error@metainfo")))
                        );
                        if (!loadedFile.delete()) {
                            loadedFile.deleteOnExit();
                        }
                    }
                    return true;
                }
            } catch (RemoteException | NotBoundException e) {
                Logger.getLogger().warn("Unable to connect to remote service", e);
            }
        }
        TaskOutput.put(Level.Warn, Language.get(DownloadPackages.class, "task@package.skip"));
        return false;
    }

    private static File loadPackageFile(IPluginLoaderService pluginLoader, IPluginLoaderService.RemotePackage remotePackage) {
        try {
            String remoteChecksumStr = pluginLoader.getPackageFileChecksum(remotePackage.getId(), remotePackage.getVersion());
            File upgradeFile = new File(
                    PluginManager.getInstance().getPluginLoader().getPluginDir(),
                    MessageFormat.format("{0}-{1}.jar", remotePackage.getTitle(), remotePackage.getVersion())
            );

            MessageDigest localChecksum = MessageDigest.getInstance("MD5");
            try (
                    FileOutputStream outStream = new FileOutputStream(upgradeFile);
                    RemoteInputStream inStream = pluginLoader.getPackageFileStream(remotePackage.getId(), remotePackage.getVersion())
            ) {
                byte[] data = new byte[1024];
                long totalRead = 0;
                int bytesRead = inStream.read(data);
                while (bytesRead != -1) {
                    totalRead = totalRead + bytesRead;
                    outStream.write(data, 0, bytesRead);
                    localChecksum.update(data, 0, bytesRead);
                    bytesRead = inStream.read(data);
                }
            }
            TaskOutput.put(Level.Debug, fillStepResult(STEP_DOWNLOAD, null, null));

            String localChecksumStr = DatatypeConverter.printHexBinary(localChecksum.digest());
            if (localChecksumStr.equals(remoteChecksumStr)) {
                TaskOutput.put(Level.Debug, fillStepResult(STEP_CHECKSUM, null, null));
                return upgradeFile;
            } else {
                String errorMsg = MessageFormat.format(
                    "Checksum verification error: expected={0}, loaded={1}",
                        remoteChecksumStr, DatatypeConverter.printHexBinary(localChecksum.digest())
                );
                Exception exception = new Exception(errorMsg);
                TaskOutput.put(Level.Warn, fillStepResult(STEP_CHECKSUM, null, exception));
                Files.delete(upgradeFile.toPath());
                throw exception;
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Unable to download updated package: {0}", e.getMessage());
            return null;
        }
    }


    private static void installPackageFile(File jarFile) {
        PluginPackage newPackage;
        try {
            newPackage = new PluginPackage(jarFile);
        } catch (IOException e) {
            throw new Error(Language.get(DownloadPackages.class, "error@metainfo"));
        }
        PluginPackage oldPackage = PluginManager.getInstance().getPluginLoader().getPackageById(newPackage.getId());
        PackageView oldPkgView, newPkgView;
        boolean needPublish =
                oldPackage == null || (
                        (oldPkgView = PluginManager.getInstance().getPluginCatalog().getView(oldPackage)) != null &&
                        oldPkgView.isPublished()
                );

        if (oldPackage != null) {
            PluginManager.getInstance().getPluginLoader().replacePluginPackage(newPackage);
        } else {
            PluginManager.getInstance().getPluginLoader().addPluginPackage(newPackage);
        }
        if (needPublish) {
            newPkgView = PluginManager.getInstance().getPluginCatalog().getView(newPackage);
            if (newPkgView != null) {
                newPkgView.getCommand(PackageView.PublishPackage.class).execute(newPkgView, Collections.emptyMap());
                TaskOutput.put(
                        Level.Debug,
                        fillStepResult(STEP_PUBLISH, null, null)
                );
            }
        }
    }


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
                        "<font color='red'>&#x26D4;</font><br/>   {0}",
                        error.getMessage()
                ));
            } else {
                builder.append("<font color='red'>&#x26D4;</font>");
            }
        }
        return builder.toString();
    }


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
                new TaskLoadUpdates(context)
        );
    }

    private final static class TaskLoadUpdates extends AbstractTask<Void> {

        private final List<RemotePackageView> packages;

        TaskLoadUpdates(List<RemotePackageView> packages) {
            super(Language.get(DownloadPackages.class, "task@title"));
            this.packages = packages;
        }

        @Override
        public Void execute() throws Exception {
            Dialog dialog = new Dialog(
                    Dialog.findNearestWindow(),
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
                    null,
                    Dialog.Default.BTN_CLOSE
            );

            SwingUtilities.invokeLater(() -> {
                dialog.setPreferredSize(new Dimension(560, 400));
                dialog.setResizable(false);
                dialog.setVisible(true);
            });

            for (RemotePackageView pkgView : packages) {
                setProgress(
                        100*(packages.indexOf(pkgView)) / packages.size(),
                        MessageFormat.format(Language.get(DownloadPackages.class, "task@progress"), pkgView.remotePackage)
                );
                importPackage(pkgView);
            }
            setProgress(100, Status.FINISHED.getDescription());
            return null;
        }

        private void importPackage(RemotePackageView pkgView) {
            TaskOutput.put(
                    Level.Debug,
                    Language.get(DownloadPackages.class, "task@package.start"),
                    ImageUtils.toBase64(pkgView.getIcon()),
                    (int) (IEditor.FONT_VALUE.getSize() * 1.7),
                    pkgView.remotePackage.getTitle(),
                    pkgView.remotePackage.getVersion()
            );
            installPackage(pkgView.remotePackage);
        }

        @Override
        public void finished(Void result) {

        }
    }
}
