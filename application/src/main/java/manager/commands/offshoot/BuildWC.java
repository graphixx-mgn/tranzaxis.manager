package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.log.Logger;
import codex.model.ParamModel;
import codex.property.PropertyHolder;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import codex.utils.Runtime;
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
import java.text.MessageFormat;
import java.util.Map;
import java.util.stream.Stream;

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

    public static Throwable getRootCause(Throwable exception) {
        return Stream
                .iterate(exception, Throwable::getCause)
                .filter(element -> element.getCause() == null)
                .findFirst()
                .orElse(null);
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
    protected void preprocessParameters(ParamModel paramModel) {
        super.preprocessParameters(paramModel);
    }

    @Override
    public ITask getTask(Offshoot context, Map<String, IComplexType> map) {
        if (Runtime.JVM.compiler.get() == null) {
            MessageBox.show(MessageType.ERROR, Language.get(BuildWC.class, "compiler@notfound"));
            return null;
        }
        return new GroupTask(
                MessageFormat.format(
                        "{0}: ''{1}/{2}''",
                        Language.get("title"),
                        context.getRepository().getPID(),
                        context.getPID()
                ),
                new BuildKernelTask(context),
                new BuildSourceTask(context, map.get(PARAM_CLEAN).getValue() == Boolean.TRUE)
        );
    }

    @Override
    public void execute(Offshoot offshoot, Map<String, IComplexType> map) {}
}
