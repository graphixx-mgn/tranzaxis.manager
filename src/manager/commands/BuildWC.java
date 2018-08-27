
package manager.commands;

import codex.command.EntityCommand;
import codex.log.Logger;
import codex.model.Entity;
import codex.property.PropertyHolder;
import codex.service.ServiceRegistry;
import codex.task.AbstractTask;
import codex.task.ExecuteException;
import codex.task.GroupTask;
import codex.task.ITask;
import codex.task.ITaskExecutorService;
import codex.task.ITaskListener;
import codex.task.Status;
import codex.task.TaskManager;
import codex.type.Bool;
import codex.type.IComplexType;
import codex.utils.ImageUtils;
import codex.utils.Language;
import com.sun.javafx.PlatformUtil;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import manager.commands.build.BuildingNotifier;
import manager.commands.build.IBuildingNotifier;
import manager.commands.build.KernelBuilder;
import manager.commands.build.SourceBuilder;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
import manager.type.WCStatus;
import org.apache.tools.ant.util.DateUtils;

public class BuildWC extends EntityCommand {
    
    private static final ITaskExecutorService TES = ((ITaskExecutorService) ServiceRegistry.getInstance().lookupService(TaskManager.TaskExecutorService.class));
    
    public  static final Integer    RMI_PORT = 2099;  
    private static Registry         RMI_REGISTRY;
    private static BuildingNotifier BUILD_NOTIFIER;
    
    static {
        try {
            RMI_REGISTRY = LocateRegistry.createRegistry(RMI_PORT);
            BUILD_NOTIFIER = new BuildingNotifier();
            RMI_REGISTRY.bind(BuildingNotifier.class.getCanonicalName(), BUILD_NOTIFIER);
        } catch (RemoteException | AlreadyBoundException e) {
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
                (entity) -> {
                    return entity.model.getValue("wcStatus").equals(WCStatus.Succesfull);
                }
        );
        setParameters(params);
        setGroupId("update");
    }
    
//    @Override
//    public void actionPerformed(ActionEvent event) {
//        if (getContext().length == 1) {
//            super.actionPerformed(event);
//        } else {
//            ParametersDialog paramDialog = new ParametersDialog(this, () -> {
//                return Stream.concat(
//                        Arrays.stream(params), 
//                        Arrays.stream(new PropertyHolder[] { new PropertyHolder("sequential", new Bool(Boolean.FALSE), true) })
//                ).toArray(PropertyHolder[]::new);
//            });
//            try {
//                Map<String, IComplexType> paramValues = paramDialog.call();
//                if (paramValues.get("sequential").getValue() == Boolean.TRUE) {
//                    final List<ITask> sequence = new LinkedList<>();
//                    for (Entity entity : getContext()) {
//                        sequence.add(
//                                new GroupTask<>(
//                                        Language.get("title") + ": "+((Offshoot) entity).getWCPath(),
//                                        new BuildKernelTask((Offshoot) entity),
//                                        new BuildSourceTask((Offshoot) entity, paramValues.get("clean").getValue() == Boolean.TRUE)
//                                )
//                        );
//                    }
//                    TES.enqueueTask(new GroupTask(
//                            "[SEQUENTIAL]", 
//                            sequence.toArray(new ITask[] {})
//                    ));
//                } else {
//                    SwingUtilities.invokeLater(() -> {
//                        Logger.getLogger().debug("Perform command [{0}]. Context: {1}", getName(), Arrays.asList(getContext()));
//                        for (Entity entity : getContext()) {
//                            execute(entity, paramValues);
//                        }
//                        activate();
//                    });
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
    
    @Override
    public boolean multiContextAllowed() {
        return true;
    }
    
    @Override
    public void execute(Entity entity, Map<String, IComplexType> map) {
        executeTask(
                entity,
                new GroupTask<>(
                        Language.get("title") + ": \""+((Offshoot) entity).getWCPath()+"\"",
                        new BuildKernelTask((Offshoot) entity),
                        new BuildSourceTask((Offshoot) entity, map.get("clean").getValue() == Boolean.TRUE)
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
    
    public class BuildKernelTask extends AbstractTask<Void> {
        
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
                .add(offshoot.getWCPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("bin")
                .add("*");
            StringJoiner radixLibPath = new StringJoiner(File.separator)
                .add(offshoot.getWCPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("lib")
                .add("*");
            classPath = radixBinPath+";"+radixLibPath+";"+classPath+";"+javac;
            command.add("-cp");
            command.add(classPath);

            command.add(KernelBuilder.class.getCanonicalName());

            command.add(uid.toString());
            command.add(offshoot.getWCPath());

            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (currentJar.isFile()) {
                builder.directory(currentJar.getParentFile());
            } else {
                builder.directory(currentJar);
            }
            
            try {
                long startTime = System.currentTimeMillis();
                AtomicReference errorRef = new AtomicReference(null);
                
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
                                new Object[]{offshoot.getWCPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        );
                        errorRef.set(new ExecuteException(
                                MessageFormat.format(
                                        Language.get(BuildWC.class.getSimpleName(), "command@seelog"), 
                                        offshoot.getWCPath()+File.separator+"build-kernel.log"
                                ),
                                message+"\n                     "+ex.getMessage().replaceAll("\n", "\n                     ")
                        ));
                    }

                    @Override
                    public void finished() {
                        Logger.getLogger().info(MessageFormat.format(
                                "BUILD KERNEL [{0}] finished. Total time: {1}", 
                                new Object[]{offshoot.getWCPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        ));
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
                    throw (Exception) errorRef.get();
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
    
    public class BuildSourceTask extends AbstractTask<Error> {
        
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
                .add(offshoot.getWCPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("bin")
                .add("*");
            StringJoiner radixLibPath = new StringJoiner(File.separator)
                .add(offshoot.getWCPath())
                .add("org.radixware")
                .add("kernel")
                .add("common")
                .add("lib")
                .add("*");
            classPath = radixBinPath+";"+radixLibPath+";"+classPath;
            command.add("-cp");
            command.add(classPath);
            
            command.add(SourceBuilder.class.getCanonicalName());
            
            command.add(uid.toString());
            command.add(offshoot.getWCPath());
            command.add(clean ? "1" : "0");
            
            final ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (currentJar.isFile()) {
                builder.directory(currentJar.getParentFile());
            } else {
                builder.directory(currentJar);
            }
            try {
                long startTime = System.currentTimeMillis();
                AtomicReference errorRef = new AtomicReference(null);
                
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
                                new Object[]{offshoot.getWCPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        );
                        errorRef.set(new ExecuteException(
                                message,
                                message+"\n                     "+ex.getMessage().replaceAll("\n", "\n                     ")
                        ));
                    }

                    @Override
                    public void finished() {
                        Logger.getLogger().info(MessageFormat.format(
                                "BUILD SOURCE [{0}] finished. Total time: {1}", 
                                new Object[]{offshoot.getWCPath(), DateUtils.formatElapsedTime(System.currentTimeMillis() - startTime)}
                        ));
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
                    offshoot.model.setValue("built", new BuildStatus(offshoot.getRevision(false).getNumber(), true));
                    offshoot.model.commit();
                    throw (Exception) errorRef.get();
                }
            } finally {
                BUILD_NOTIFIER.removeListener(uid);
                Runtime.getRuntime().removeShutdownHook(hook);
            }
            return null;
        }

        @Override
        public void finished(Error e) {
            offshoot.model.setValue("built", new BuildStatus(offshoot.getRevision(false).getNumber(), false));
            offshoot.model.commit();
        }
    }
    
}
