package manager;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.InstanceUnit;
import codex.log.LogUnit;
import codex.log.Logger;
import codex.notification.MessagingQueue;
import codex.scheduler.JobScheduler;
import codex.service.ServiceUnit;
import codex.task.TaskManager;
import codex.utils.ImageUtils;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import manager.nodes.Common;
import manager.nodes.DatabaseRoot;
import manager.nodes.EnvironmentRoot;
import manager.nodes.RepositoryRoot;
import manager.ui.Window;
import manager.ui.splash.SplashScreen;
import manager.upgrade.UpgradeUnit;
import plugin.PluginManager;
import javax.swing.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.StringJoiner;

public class Manager {
    
    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            //
        }
    }

    public static void main(String[] args) {
        new Manager();
    }

    public Manager() {
//        new NotifyMessage("Test", "TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST", 0, null).setVisible(true);
        SplashScreen splash = new SplashScreen();
        Window window = new Window("TranzAxis Manager", ImageUtils.getByPath("/images/project.png"));

        String uniqueAppId = Manager.class.getCanonicalName();
        try {
            JUnique.acquireLock(uniqueAppId, (message) -> {
                window.setState(JFrame.NORMAL);
                window.setExtendedState(window.getExtendedState());
                window.toFront();
                window.requestFocus();
                return null;
            });
        } catch (AlreadyLockedException e) {
            JUnique.sendMessage(uniqueAppId, "OPEN");
            System.exit(0);
        }

        splash.setProgress(5, "Load system services");
        LogUnit logViewer = LogUnit.getInstance();
        systemInfo();

        splash.setProgress(35, "Start messaging unit");
        MessagingQueue messageQueue = new MessagingQueue();

        splash.setProgress(40, "Start plugin manager");
        PluginManager pluginManager = PluginManager.getInstance();

        splash.setProgress(45, "Start scheduler");
        JobScheduler scheduler = JobScheduler.getInstance();

        splash.setProgress(50, "Start task management system");
        TaskManager taskManager = new TaskManager();

        splash.setProgress(65, "Build configuration root");
        Common root = new Common();
        NodeTreeModel objectsTree = new NodeTreeModel(root);
        ExplorerUnit configExplorer = ExplorerUnit.getInstance();
        configExplorer.setModel(objectsTree);

        root.attach(new RepositoryRoot());
        root.attach(new DatabaseRoot());
        root.attach(new EnvironmentRoot());

        //splash.setProgress(75, "Start command launcher unit");
        //LauncherUnit commandLauncher = new LauncherUnit();

        splash.setProgress(80, "Start service management unit");
        ServiceUnit serviceOptions = new ServiceUnit();

        splash.setProgress(85, "Start instance control unit");
        InstanceUnit networkBrowser = InstanceUnit.getInstance();

        splash.setProgress(90, "Start upgrade unit");
        UpgradeUnit upgradeUnit = new UpgradeUnit();

        splash.setProgress(95, "Initialize user interface");
        window.addUnit(logViewer,   window.loggingPanel);
        window.addUnit(upgradeUnit, window.upgradePanel);
        window.addUnit(taskManager, window.taskmgrPanel);

        window.addUnit(configExplorer);
        //window.addUnit(commandLauncher);
        window.addUnit(scheduler);
        window.addUnit(serviceOptions);
        window.addUnit(messageQueue);
        window.addUnit(networkBrowser);
        window.addUnit(pluginManager);

        splash.setVisible(false);
        window.setVisible(true);
    }

    private void systemInfo() {
        String javac = null;
        Logger.getLogger().debug("Collect JVM information");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            Logger.getLogger().warn("Java compiler not found");
        } else {
            URL url = compiler.getClass().getProtectionDomain().getCodeSource().getLocation();
            try {
                String urlDecoded = URLDecoder.decode(url.getPath(), "UTF-8");
                javac = new File(urlDecoded).getPath();
            } catch (UnsupportedEncodingException e) {
                Logger.getLogger().warn("Java compiler not found");
            }
        }
        Logger.getLogger().info(
                new StringJoiner("\n")
                        .add("JVM information:")
                        .add("Name:     {0}")
                        .add("Version:  {1}")
                        .add("Path:     {2}")
                        .add("Compiler: {3}")
                        .toString(),
                System.getProperty("java.runtime.name"),
                System.getProperty("java.vm.specification.version"),
                System.getProperty("java.home"),
                javac == null ? "<not found>" : javac
        );
    }
}
