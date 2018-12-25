package manager.upgrade;

import codex.component.button.DialogButton;
import codex.component.dialog.Dialog;
import codex.log.Logger;
import codex.log.TextPaneAppender;
import codex.task.AbstractTask;
import codex.task.AbstractTaskView;
import codex.task.ExecuteException;
import codex.task.ITask;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.utils.ImageUtils;
import codex.utils.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.ScrollPaneLayout;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.xml.bind.DatatypeConverter;
import manager.upgrade.stream.RemoteInputStream;
import manager.xml.Version;
import manager.xml.VersionsDocument;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;

public class Updater {
    
    private final File    originalFile;
    private final File    upgradedFile;
    private final String  host;
    private final Integer port;
    private final ITask   task;
    private final Output  out;
    private final Dialog  wnd;
    
    Updater(String host, int port) {
        this.originalFile = UpgradeService.getCurrentJar();
        this.upgradedFile = new File(
            originalFile.getParentFile(),
            FilenameUtils.removeExtension(originalFile.getName()).concat("-upgrade.jar")
        );
        upgradedFile.deleteOnExit();
        
        this.host = host;
        this.port = port;
        this.task = new LoadUpgradeFile();
        this.out  = new Output();
        
        DialogButton restartBtn = Dialog.Default.BTN_OK.newInstance();
        restartBtn.setText(Language.get(UpgradeUnit.class.getSimpleName(), "process@restart"));
        restartBtn.setEnabled(false);
        
        DialogButton closeBtn = Dialog.Default.BTN_CLOSE.newInstance();
        closeBtn.setVisible(false);
        
        TimerTask countdown = new TimerTask() {
            int second = 6;
            
            @Override
            public void run() {
                second--;
                if (second == 0) {
                    cancel();
                    restartBtn.click();
                } else {
                    restartBtn.setText(MessageFormat.format(
                            Language.get(UpgradeUnit.class.getSimpleName(), "process@deferred"), 
                            second
                    ));

                    FontMetrics metrics = restartBtn.getFontMetrics(restartBtn.getFont()); 
                    int width  = metrics.stringWidth(restartBtn.getText());
                    int height = restartBtn.getPreferredSize().height;
                    Dimension newDimension = new Dimension(width+60, height);
                    restartBtn.setPreferredSize(newDimension);
                    restartBtn.setBounds(new Rectangle(restartBtn.getLocation(), restartBtn.getPreferredSize()));
                }
            } 
        };
        
        task.addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status status) {
                if (task.getStatus().equals(Status.FINISHED)) {
                    restartBtn.setEnabled(true);
                    new Timer().schedule(countdown, 0, 1000);
                    wnd.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                } else if (task.getStatus().equals(Status.FAILED)) {
                    closeBtn.setVisible(true);
                    restartBtn.setVisible(false);
                    wnd.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        });
        this.wnd = new Dialog(
                null, 
                ImageUtils.getByPath("/images/upgrade.png"), 
                Language.get(UpgradeUnit.class.getSimpleName(), "process@title"), 
                new JPanel(new BorderLayout()) {{
                    setBorder(new EmptyBorder(5, 5, 5, 5));
                    AbstractTaskView taskView = task.createView(null);
                    taskView.setBorder(new CompoundBorder(
                            new LineBorder(Color.LIGHT_GRAY, 1),
                            new EmptyBorder(5, 5, 5, 5)
                    ));
                    add(taskView, BorderLayout.NORTH);
                    add(out, BorderLayout.CENTER);
                }},
                (e) -> {
                    if (e.getID() == Dialog.OK) {
                        restart();
                    } else {
                        upgradedFile.delete();
                    }
                }, restartBtn, closeBtn
        );
    }
    
    void start() {
        Executors.newFixedThreadPool(1).submit(task);
        wnd.setVisible(true);
    }
    
    void restart() {
        final ArrayList<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(originalFile.getAbsolutePath());
        final ProcessBuilder builder = new ProcessBuilder(command);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (
                FileChannel src  = new FileInputStream(upgradedFile).getChannel();
                FileChannel dest = new FileOutputStream(originalFile).getChannel();
            ) {
                dest.transferFrom(src, 0, src.size());
                builder.start();
            } catch (IOException e) {
                Logger.getLogger().error("Error", e);
            }
        }));
        System.exit(0);
    }
    
    private final class LoadUpgradeFile extends AbstractTask<Void> {

        LoadUpgradeFile() {
            super(Language.get(UpgradeUnit.class.getSimpleName(), "process@task"));
        }

        @Override
        public Void execute() throws Exception {
            Thread.sleep(500);
            
            Registry rmiRegistry = LocateRegistry.getRegistry(host, port);
            IUpgradeService remoteUpService = (IUpgradeService) rmiRegistry.lookup(UpgradeService.class.getCanonicalName());
            Logger.getLogger().info(Language.get(UpgradeUnit.class.getSimpleName(), "process@connect"), host, String.valueOf(port));
            
            Version localVersion  = UpgradeService.getVersion();
            Version remoteVersion = remoteUpService.getCurrentVersion();
            VersionsDocument diff = remoteUpService.getDiffVersions(localVersion, remoteVersion);
            List<Version> chain = new LinkedList<>(Arrays.asList(diff.getVersions().getVersionArray()));
            chain.add(0, localVersion);
            Logger.getLogger().info(
                    Language.get(UpgradeUnit.class.getSimpleName(), "process@sequence"), 
                    chain.stream().map((version) -> {
                        return version.getNumber();
                    }).collect(Collectors.joining(" => "))
            );
            
            try (
                    RemoteInputStream inStream  = remoteUpService.getUpgradeFileStream();
                    FileOutputStream  outStream = new FileOutputStream(upgradedFile);
            ) {
                long fileSize = inStream.available();
                String remoteChecksum = remoteUpService.getUpgradeFileChecksum();
                Logger.getLogger().info(
                        Language.get(UpgradeUnit.class.getSimpleName(), "process@file"), 
                        "\n", formatFileSize(fileSize), remoteChecksum
                );
                
                MessageDigest localChecksum = MessageDigest.getInstance("MD5");
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
                Logger.getLogger().info(Language.get(UpgradeUnit.class.getSimpleName(), "process@loaded"));
                
                if (DatatypeConverter.printHexBinary(localChecksum.digest()).equals(remoteChecksum)) {
                    Logger.getLogger().info(Language.get(UpgradeUnit.class.getSimpleName(), "process@result.success"));
                } else {
                    throw new ExecuteException(
                            Language.get(UpgradeUnit.class.getSimpleName(), "process@error"), 
                            Language.get(UpgradeUnit.class.getSimpleName(), "process@result.error")
                    );
                }                
            }
            return null;
        }

        @Override
        public void finished(Void result) {}
    }
    
    private final class Output extends JPanel {
        
        Output() {
            super(new BorderLayout());
            
            JTextPane infoPane = new JTextPane();
            infoPane.setEditable(false);
            infoPane.setPreferredSize(new Dimension(450, 150));
            infoPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

            JScrollPane scrollPane = new JScrollPane();
            scrollPane.setLayout(new ScrollPaneLayout());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.getViewport().add(infoPane);
            scrollPane.setBorder(new CompoundBorder(
                    new EmptyBorder(5, 0, 0, 0), 
                    new LineBorder(Color.LIGHT_GRAY, 1)
            ));
            add(scrollPane, BorderLayout.CENTER);
            
            TextPaneAppender paneAppender = new TextPaneAppender(infoPane) {
                @Override
                protected void append(LoggingEvent event) {
                    String message = getLayout().format(event).trim().replaceAll("\n                     ", "\n");
                    LoggingEvent catchedEvent = new LoggingEvent(
                            event.getFQNOfLoggerClass(),
                            event.getLogger(),
                            event.getLevel(),
                            message,
                            event.getThrowableInformation() == null ? null : event.getThrowableInformation().getThrowable()
                    );
                    super.append(catchedEvent);
                }
            };
            paneAppender.setThreshold(Priority.INFO);
            paneAppender.setLayout(new PatternLayout("%m%n"));
            Logger.getLogger().addAppender(paneAppender);
            
            Style style = infoPane.getStyle(Level.INFO.toString());
            StyleConstants.setForeground(style, Color.GRAY);
        }
        
    }
    
    private static String formatFileSize(long size) {
        String hrSize;

        double bytes     = size;
        double kilobytes = size/1024.0;
        double megabytes = ((size/1024.0)/1024.0);
        double gigabytes = (((size/1024.0)/1024.0)/1024.0);
        double terabytes = ((((size/1024.0)/1024.0)/1024.0)/1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if (terabytes > 1) {
            hrSize = dec.format(terabytes).concat(" TB");
        } else if (gigabytes > 1) {
            hrSize = dec.format(gigabytes).concat(" GB");
        } else if (megabytes > 1) {
            hrSize = dec.format(megabytes).concat(" MB");
        } else if (kilobytes > 1) {
            hrSize = dec.format(kilobytes).concat(" KB");
        } else {
            hrSize = dec.format(bytes).concat(" B");
        }
        return hrSize;
    }
    
}
