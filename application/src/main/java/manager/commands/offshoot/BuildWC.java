package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.property.PropertyHolder;
import codex.task.GroupTask;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.offshoot.build.BuildKernelTask;
import manager.commands.offshoot.build.BuildSourceTask;
import manager.commands.offshoot.build.BuildingNotifier;
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.util.Map;

@EntityCommand.Definition(parentCommand = RefreshWC.class)
public class BuildWC extends EntityCommand<Offshoot> {

    private static final ImageIcon COMMAND_ICON = ImageUtils.combine(
            ImageUtils.getByPath("/images/folder.png"),
            ImageUtils.resize(ImageUtils.getByPath("/images/build.png"), .7f),
            SwingConstants.SOUTH_EAST
    );
    private static final String PARAM_CLEAN = "clean";

    private static BuildingNotifier BUILD_NOTIFIER;
    private static ServerSocket     RMI_SOCKET;

    static {
        try {
            RMI_SOCKET = new ServerSocket(0);
            BUILD_NOTIFIER = new BuildingNotifier();
            LocateRegistry.createRegistry(
                    0,
                    (host, port) -> {
                        throw new UnsupportedOperationException("Not supported yet.");
                    },
                    port -> RMI_SOCKET
            ).bind(BuildingNotifier.class.getCanonicalName(), BUILD_NOTIFIER);
        } catch (AlreadyBoundException | IOException e) {
            Logger.getLogger().error(e.getMessage());
        }
    }

    public static BuildingNotifier getBuildNotifier() {
        return BUILD_NOTIFIER;
    }

    public static int getPort() {
        return RMI_SOCKET.getLocalPort();
    }

    public BuildWC() {
        super(
                "build",
                Language.get("title"),
                COMMAND_ICON,
                Language.get("desc"), 
                (offshoot) -> offshoot.getWCStatus().equals(WCStatus.Successful)
        );
        setParameters(
                new PropertyHolder<>(PARAM_CLEAN, new Bool(Boolean.FALSE), true)
        );
    }
    
    @Override
    public boolean multiContextAllowed() {
        return true;
    }
    
    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> map) {
        executeTask(
                offshoot,
                new GroupTask<>(
                        Language.get("title") + ": '"+(offshoot).getLocalPath()+"'",
                        new BuildKernelTask(offshoot),
                        new BuildSourceTask(offshoot, map.get(PARAM_CLEAN).getValue() == Boolean.TRUE)
                ),
                false
        );
    }
}
