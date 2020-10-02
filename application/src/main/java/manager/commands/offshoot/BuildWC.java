package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.component.messagebox.MessageBox;
import codex.component.messagebox.MessageType;
import codex.context.IContext;
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
import manager.nodes.Offshoot;
import manager.type.WCStatus;
import javax.swing.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.text.MessageFormat;
import java.util.HashMap;
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

    public static class RMIRegistry {

        private final ServerSocket socket;
        private final Registry registry;
        private final Map<String, ServerSocket> serviceSocketMap = new HashMap<>();

        public RMIRegistry() throws IOException {
            socket = new ServerSocket(0);
            registry = LocateRegistry.createRegistry(0,
                    (host, port) -> {
                        throw new UnsupportedOperationException("Not supported yet.");
                    },
                    port -> socket
            );
        }

        public void registerService(String name, Remote service) throws IOException {
            try {
                ServerSocket serviceSocket = serviceSocketMap.computeIfAbsent(name, s -> {
                    try {
                        return new ServerSocket(0);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                });
                if (serviceSocket != null) {
                    UnicastRemoteObject.exportObject(service, 0, new CSFactory(), new SSFactory(serviceSocket));
                    registry.bind(name, service);
                }
            } catch (AlreadyBoundException ignore) {}
        }

        public final int getPort() {
            return socket.getLocalPort();
        }

        public final void close() throws IOException {
            for (String name : registry.list()) {
                try {
                    Remote service = registry.lookup(name);
                    registry.unbind(name);
                    UnicastRemoteObject.unexportObject(service, true);
                    ServerSocket serviceSocket = serviceSocketMap.remove(name);
                    if (serviceSocket != null) {
                        if (!serviceSocket.isClosed()) {
                            serviceSocket.close();
                        }
                    }
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
            UnicastRemoteObject.unexportObject(registry, true);
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }

    private final static class CSFactory implements RMIClientSocketFactory, Serializable {

        private CSFactory() {}

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return new Socket(host, port);
        }
    }

    private final static class SSFactory implements RMIServerSocketFactory, Serializable {

        private final ServerSocket socket;

        private SSFactory(ServerSocket socket) {
            this.socket = socket;
        }


        @Override
        public ServerSocket createServerSocket(int port) {
            return socket;
        }
    }
}
