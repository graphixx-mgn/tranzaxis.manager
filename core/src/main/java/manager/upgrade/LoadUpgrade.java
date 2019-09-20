package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.instance.Instance;
import codex.log.Level;
import codex.log.Logger;
import codex.task.*;
import codex.utils.FileUtils;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.upgrade.stream.RemoteInputStream;
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
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

class LoadUpgrade extends AbstractTask<Void> {

    private final Instance instance;
    private final File     originalFile;
    private final File     upgradedFile;

    private final DialogButton restartBtn = Dialog.Default.BTN_OK.newInstance();
    private final DialogButton closeBtn = Dialog.Default.BTN_CLOSE.newInstance();
    private final Dialog dialog;

    LoadUpgrade(Instance instance) {
        super(Language.get(UpgradeUnit.class, "process@task"));
        this.instance = instance;
        this.originalFile = UpgradeService.getCurrentJar();
        this.upgradedFile = new File(
                originalFile.getParentFile(),
                FilenameUtils.removeExtension(originalFile.getName()).concat("-upgrade.jar")
        );
        upgradedFile.deleteOnExit();
    }

    {
        restartBtn.setText(Language.get(UpgradeUnit.class, "process@restart"));
        restartBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        dialog = new Dialog(
                null,
                ImageUtils.getByPath("/images/upgrade.png"),
                Language.get(UpgradeUnit.class, "process@title"),
                new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(5, 5, 5, 5));
                    AbstractTaskView taskView = LoadUpgrade.this.createView(null);
                    taskView.setBorder(new CompoundBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1),
                            new EmptyBorder(5, 5, 5, 5)
                    ));
                    add(taskView, BorderLayout.NORTH);
                    add(TaskOutput.createOutput(LoadUpgrade.this), BorderLayout.CENTER);
                }},
                (e) -> {
                    if (e.getID() == Dialog.OK) {
                        restart();
                    }
                },
                restartBtn, closeBtn
        );
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    @Override
    public Void execute() throws Exception {
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        Thread.sleep(500);

        InetSocketAddress rmiAddress = instance.getRemoteAddress();
        String host = rmiAddress.getAddress().getHostAddress();
        int port = rmiAddress.getPort();

        Registry rmiRegistry = LocateRegistry.getRegistry(host, port);
        IUpgradeService remoteUpService = (IUpgradeService) rmiRegistry.lookup(UpgradeService.class.getCanonicalName());
        TaskOutput.put(Level.Debug, Language.get(UpgradeUnit.class, "process@connect"), host, String.valueOf(port));

        Version localVersion  = UpgradeService.getVersion();
        Version remoteVersion = remoteUpService.getCurrentVersion();
        VersionsDocument diff = remoteUpService.getDiffVersions(localVersion, remoteVersion);
        List<Version> chain = new LinkedList<>(Arrays.asList(diff.getVersions().getVersionArray()));
        chain.add(0, localVersion);
        TaskOutput.put(
                Level.Debug,
                Language.get(UpgradeUnit.class, "process@sequence"),
                chain.stream()
                        .map(Version::getNumber)
                        .collect(Collectors.joining(" => "))
        );

        String remoteChecksum = remoteUpService.getUpgradeFileChecksum();
        MessageDigest localChecksum = MessageDigest.getInstance("MD5");
        try (
            FileOutputStream outStream = new FileOutputStream(upgradedFile)
        ) {
            RemoteInputStream inStream = remoteUpService.getUpgradeFileStream();

            long fileSize = inStream.available();
            TaskOutput.put(
                    Level.Debug,
                    Language.get(UpgradeUnit.class, "process@file"),
                    "\n", FileUtils.formatFileSize(fileSize), remoteChecksum
            );
            byte[] data = new byte[1024];
            long totalRead = 0;
            int  bytesRead = inStream.read(data);
            while (bytesRead != -1) {
                totalRead = totalRead + bytesRead;
                outStream.write(data, 0, bytesRead);
                localChecksum.update(data, 0, bytesRead);
                setProgress((int) (100 * totalRead / fileSize), getDescription());
                bytesRead = inStream.read(data);
            }
            TaskOutput.put(Level.Debug, Language.get(UpgradeUnit.class, "process@loaded"));
            try {
                inStream.close();
            } catch (IOException e) {
                // Do nothing
            }
        } catch (IOException e) {
            e.printStackTrace();
            closeBtn.setEnabled(true);
            upgradedFile.delete();
            TaskOutput.put(Level.Warn, Language.get(UpgradeUnit.class, "process@transmission.error"));
        } finally {
            if (DatatypeConverter.printHexBinary(localChecksum.digest()).equals(remoteChecksum)) {
                TaskOutput.put(Level.Debug, Language.get(UpgradeUnit.class, "process@result.success"));
            } else {
                closeBtn.setEnabled(true);
                throw new ExecuteException(
                        Language.get(UpgradeUnit.class, "process@error"),
                        Language.get(UpgradeUnit.class, "process@result.error")
                );
            }
        }
        return null;
    }

    @Override
    public void finished(Void result) {
        restartBtn.setEnabled(true);
    }

    private void restart() {
        final ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(originalFile.getAbsolutePath());
        final ProcessBuilder builder = new ProcessBuilder(command);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (
                    FileChannel src  = new FileInputStream(upgradedFile).getChannel();
                    FileChannel dest = new FileOutputStream(originalFile).getChannel()
            ) {
                dest.transferFrom(src, 0, src.size());
                builder.start();
            } catch (IOException e) {
                Logger.getLogger().error("Error", e);
            }
        }));
        System.exit(0);
    }

}
