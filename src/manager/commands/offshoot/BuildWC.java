
package manager.commands.offshoot;

import codex.command.EntityCommand;
import codex.command.ParametersDialog;
import codex.log.Logger;
import codex.property.PropertyHolder;
import codex.task.AbstractTask;
import codex.task.ExecuteException;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.sun.javafx.PlatformUtil;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import manager.commands.offshoot.build.BuildingNotifier;
import manager.commands.offshoot.build.IBuildingNotifier;
import manager.commands.offshoot.build.KernelBuilder;
import manager.commands.offshoot.build.SourceBuilder;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.apache.tools.ant.util.DateUtils;

public class BuildWC extends EntityCommand<Offshoot> {
    
    public  static ServerSocket RMI_SOCKET;
    private static Registry     RMI_REGISTRY;
    private static BuildingNotifier BUILD_NOTIFIER;
    
    static {
        try {
            RMI_SOCKET   = new ServerSocket(0);
            RMI_REGISTRY = LocateRegistry.createRegistry(
                    0,
                    new RMIClientSocketFactory() {
                        @Override
                        public Socket createSocket(String host, int port) throws IOException {
                            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                        }
                    },
                    new RMIServerSocketFactory() {
                        @Override
                        public ServerSocket createServerSocket(int port) throws IOException {
                            return RMI_SOCKET;
                        }
                    }
            );
            BUILD_NOTIFIER = new BuildingNotifier();
            RMI_REGISTRY.bind(BuildingNotifier.class.getCanonicalName(), BUILD_NOTIFIER);
        } catch (AlreadyBoundException | IOException e) {
            Logger.getLogger().error(e.getMessage());
        }
    }
    
    private final PropertyHolder[] params = new PropertyHolder[] { new PropertyHolder("clean", new Bool(Boolean.FALSE), true) };
    
    public BuildWC() {
        super(
                "build", 
                "title", 
                ImageUtils.resize(ImageUtils.getByPath("/images/build.png"), 28, 28), 
                Language.get("desc"), 
                (offshoot) -> {
                    return 
                            RMI_SOCKET != null && RMI_SOCKET.isBound() &&
                            offshoot.getWCStatus().equals(WCStatus.Succesfull);
                }
        );
        setParameters(params);
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        if (getContext().size() == 1) {
            super.actionPerformed(event);
        } else {
            ParametersDialog paramDialog = new ParametersDialog(this, () -> {
                return Stream.concat(
                        Arrays.stream(params), 
                        Arrays.stream(new PropertyHolder[] { new PropertyHolder(
                                "sequence", 
                                Language.get(BuildWC.class.getSimpleName(), "sequence.title"),
                                Language.get(BuildWC.class.getSimpleName(), "sequence.desc"),
                                new Bool(Boolean.FALSE), 
                                true
                        )})
                ).toArray(PropertyHolder[]::new);
            });
            
            try {
                Map<String, IComplexType> paramValues = paramDialog.call();
                if (paramValues == null) {
                    return;
                }
                if (paramValues.get("sequence").getValue() == Boolean.TRUE) {
                    final List<ITask> sequence = new LinkedList<>();
                    for (Offshoot offshoot : getContext()) {
                        ITaskListener unlocker = new ITaskListener() {
                            @Override
                            public void statusChanged(ITask task, Status status) {
                                if (status.isFinal()) {
                                    offshoot.getLock().release();
                                }
                            }
                        };
                        
                        ITask buildKernel = new BuildKernelTask(offshoot);
                        ITask buildSource = new BuildSourceTask(offshoot, paramValues.get("clean").getValue() == Boolean.TRUE);
                        ITask buildGroup  = new GroupTask<>(
                                Language.get("title") + ": "+(offshoot).getLocalPath(),
                                buildKernel,
                                buildSource
                        );
                        
                        buildGroup.addListener(unlocker);
                        sequence.add(buildGroup);
                        offshoot.getLock().acquire();
                    }
                    executeTask(
                            null,
                            new GroupTask(
                                    Language.get(BuildWC.class.getSimpleName(), "task@sequence"), 
                                    sequence.toArray(new ITask[] {})
                            ),
                            false
                    );
                } else {
                    SwingUtilities.invokeLater(() -> {
                        Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), getContext());
                        getContext().forEach((entity) -> {
                            execute(entity, paramValues);
                        });
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                        Language.get("title") + ": \""+(offshoot).getLocalPath()+"\"",
                        new BuildKernelTask(offshoot),
                        new BuildSourceTask(offshoot, map.get("clean").getValue() == Boolean.TRUE)
                ),
                false
        );
    }
    
    static File getCurrentJar() {
        try {
            if (PlatformUtil.isWindows()) {
                return new File(BuildWC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } else {
                return new File(URLDecoder.decode(ClassLoader.getSystemClassLoader().getResource(".").getPath(), "UTF-8"));
            }
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            Logger.getLogger().error("Unexpected exception", e);
        }
        return null;
    }
    
    class BuildKernelTask extends AbstractTask<Void> {
        
        private final Offshoot offshoot;
        Process process;
        Thread  hook = new Thread() {
            @Override
            public void run() {
                if (!getStatus().isFinal()) {
                    cancel(true);
                }
            }
        };

        public BuildKernelTask(Offshoot offshoot) {
            super(Language.get(BuildWC.class.getSimpleName(), "command@kernel"));
            this.offshoot = offshoot;
        }
        
        @Override
        public boolean isPauseable() {
            return true;
        }

        @Override
        public Void execute() throws Exception {
            UUID uid = UUID.randomUUID();
            final File currentJar = getCurrentJar();

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");

            String classPath;
            if (currentJar.isFile()) {
                classPath = currentJar.getName();
            } else {
                classPath = System.getProperty("java.class.path");
            }
            String javac = System.getenv("JAVA_HOME")+File.separator+"lib"+File.separator+"tools.jar";
            StringJoiner radixBinPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("bin")
                .add("*");
            StringJoiner radixLibPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("lib")
                .add("*");
            classPath = radixBinPath+";"+radixLibPath+";"+classPath+";"+javac;
            command.add("-cp");
            command.add(classPath);

            command.add("-Dport="+RMI_SOCKET.getLocalPort());
            command.add("-Duuid="+uid.toString());
            command.add("-Dpath="+offshoot.getLocalPath());
            
            command.add(KernelBuilder.class.getCanonicalName());
            
            final ProcessBuilder builder = new ProcessBuilder(command);
            File temp = File.createTempFile("build_trace", ".tmp", new File(offshoot.getLocalPath()));
            temp.deleteOnExit();
            builder.redirectError(temp);
            builder.redirectOutput(temp);
            if (currentJar.isFile()) {
                builder.directory(currentJar.getParentFile());
            } else {
                builder.directory(currentJar);
            }
            
            try {
                long startTime = System.currentTimeMillis();
                AtomicReference<Exception> errorRef = new AtomicReference(null);
                
                BUILD_NOTIFIER.addListener(uid, new IBuildingNotifier.IBuildListener() {
                    @Override
                    public void setProgress(int percent) {
                        BuildKernelTask.this.setProgress(percent, BuildKernelTask.this.getDescription());
                    }

                    @Override
                    public void setStatus(String text) {
                        BuildKernelTask.this.setProgress(BuildKernelTask.this.getProgress(), text);
                    }

                    @Override
                    public void failed(Throwable ex) {
                        String message = MessageFormat.format(
                                "BUILD KERNEL [{0}] failed. Total time: {1}", 
                                new Object[]{offshoot.getLocalPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        );
                        errorRef.set(new ExecuteException(
                                MessageFormat.format(
                                        Language.get(BuildWC.class.getSimpleName(), "command@seelog"), 
                                        offshoot.getLocalPath()+File.separator+"build-kernel.log"
                                ),
                                message+"\n"+ex.getMessage()
                        ));
                    }

                    @Override
                    public void finished() {
                        Logger.getLogger().info(MessageFormat.format(
                                "BUILD KERNEL [{0}] finished. Total time: {1}", 
                                new Object[]{offshoot.getLocalPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        ));
                    }

                    @Override
                    public void checkPaused() {
                        BuildKernelTask.this.checkPaused();
                    }
                    
                });
                Runtime.getRuntime().addShutdownHook(hook);
                addListener(new ITaskListener() {
                    @Override
                    public void statusChanged(ITask task, Status status) {
                        if (status.equals(Status.CANCELLED)) {
                            process.destroy();
                        }
                    }
                });
                process = builder.start();
                process.waitFor();
                
                if (errorRef.get() != null) {
                    throw errorRef.get();
                }
            } finally {
                BUILD_NOTIFIER.removeListener(uid);
                Runtime.getRuntime().removeShutdownHook(hook);
            }
            return null;
        }

        @Override
        public void finished(Void t) {}
    }
    
    class BuildSourceTask extends AbstractTask<Error> {
        
        private final Offshoot offshoot;
        private final boolean  clean;
        Process process;
        Thread  hook = new Thread() {
            @Override
            public void run() {
                if (!getStatus().isFinal()) {
                    cancel(true);
                }
            }
        };
        
        public BuildSourceTask(Offshoot offshoot, boolean clean) {
            super(Language.get(BuildWC.class.getSimpleName(), "command@sources"));
            this.offshoot = offshoot;
            this.clean    = clean;
        }

        @Override
        public boolean isPauseable() {
            return true;
        }
        
        @Override
        public Error execute() throws Exception {
            UUID uid = UUID.randomUUID();
            final File currentJar = getCurrentJar();

            final ArrayList<String> command = new ArrayList<>();
            command.add("java");
                    
            String classPath;
            if (currentJar.isFile()) {
                classPath = currentJar.getName();
            } else {
                classPath = System.getProperty("java.class.path");
            }
            StringJoiner radixBinPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("bin")
                .add("*");
            StringJoiner radixLibPath = new StringJoiner(File.separator)
                .add(offshoot.getLocalPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("lib")
                .add("*");
            classPath = radixBinPath+";"+radixLibPath+";"+classPath;
            command.add("-cp");
            command.add(classPath);
            
            command.add("-Dport="+RMI_SOCKET.getLocalPort());
            command.add("-Duuid="+uid.toString());
            command.add("-Dpath="+offshoot.getLocalPath());
            command.add("-Dclean="+(clean ? "1" : "0"));
            
            command.add(SourceBuilder.class.getCanonicalName());
            
            final ProcessBuilder builder = new ProcessBuilder(command);
            File temp = File.createTempFile("build_trace", ".tmp", new File(offshoot.getLocalPath()));
            temp.deleteOnExit();
            builder.redirectError(temp);
            builder.redirectOutput(temp);
            if (currentJar.isFile()) {
                builder.directory(currentJar.getParentFile());
            } else {
                builder.directory(currentJar);
            }
            try {
                long startTime = System.currentTimeMillis();
                AtomicReference<Exception> errorRef = new AtomicReference(null);
                
                BUILD_NOTIFIER.addListener(uid, new IBuildingNotifier.IBuildListener() {
                    @Override
                    public void setProgress(int percent) {
                        BuildSourceTask.this.setProgress(percent, BuildSourceTask.this.getDescription());
                    }

                    @Override
                    public void setStatus(String text) {
                        BuildSourceTask.this.setProgress(BuildSourceTask.this.getProgress(), text);
                    }

                    @Override
                    public void failed(Throwable ex) {
                        String message = MessageFormat.format(
                                "BUILD SOURCE [{0}] failed. Total time: {1}", 
                                new Object[]{offshoot.getLocalPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        );
                        errorRef.set(new ExecuteException(
                                message,
                                message+"\n".concat(ex.getMessage())
                        ));
                    }

                    @Override
                    public void finished() {
                        Logger.getLogger().info(MessageFormat.format(
                                "BUILD SOURCE [{0}] finished. Total time: {1}", 
                                new Object[]{offshoot.getLocalPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        ));
                    }

                    @Override
                    public void checkPaused() {
                        BuildSourceTask.this.checkPaused();
                    }
                });
                Runtime.getRuntime().addShutdownHook(hook);
                addListener(new ITaskListener() {
                    @Override
                    public void statusChanged(ITask task, Status status) {
                        if (status.equals(Status.CANCELLED)) {
                            process.destroy();
                        }
                    }
                });
                process = builder.start();
                process.waitFor();
                
                if (errorRef.get() != null) {
                    offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), true));
                    try {
                        offshoot.model.commit(false);
                    } catch (Exception e) {}
                    throw errorRef.get();
                }
            } finally {
                BUILD_NOTIFIER.removeListener(uid);
                Runtime.getRuntime().removeShutdownHook(hook);
            }
            return null;
        }

        @Override
        public void finished(Error err) {
            if (!isCancelled()) {
                offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), false));
                try {
                    offshoot.model.commit(false);
                } catch (Exception e) {}
            }
        }
    }
    
}
