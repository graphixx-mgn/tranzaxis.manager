package manager;

import codex.explorer.ExplorerUnit;
import codex.explorer.tree.NodeTreeModel;
import codex.instance.InstanceUnit;
import codex.log.LogUnit;
import codex.notification.MailBox;
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

        splash.setProgress(35, "Start messaging unit");
        MailBox mailCenter = MailBox.getInstance();

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
        window.addUnit(scheduler);
        window.addUnit(serviceOptions);
        window.addUnit(mailCenter);
        window.addUnit(networkBrowser);
        window.addUnit(pluginManager);

        splash.setVisible(false);
        window.setVisible(true);
    }
}
