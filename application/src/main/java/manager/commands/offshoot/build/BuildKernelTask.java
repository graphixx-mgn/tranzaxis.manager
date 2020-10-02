package manager.commands.offshoot.build;

import codex.log.Logger;
import codex.task.*;
import codex.utils.Language;
import codex.utils.Runtime;
import manager.commands.offshoot.BuildWC;
import manager.nodes.Offshoot;
import org.apache.tools.ant.util.DateUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

public class BuildKernelTask extends AbstractTask<Void> {

    private final Offshoot offshoot;
    private final Thread  hook = new Thread(() -> {
        if (!getStatus().isFinal()) {
            cancel(true);
        }
    });

    public BuildKernelTask(Offshoot offshoot) {
        super(MessageFormat.format(
                Language.get(BuildWC.class, "command@kernel"),
                offshoot.getRepository().getPID(),
                offshoot.getPID()
        ));
        this.offshoot = offshoot;
    }

    @Override
    public boolean isPauseable() {
        return true;
    }

    @Override
    public Void execute() throws Exception {
        BuildWC.RMIRegistry rmiRegistry = new BuildWC.RMIRegistry();
        final File currentJar = Runtime.APP.jarFile.get();

        final ArrayList<String> command = new ArrayList<>();
        command.add("java");

        String classPath;
        if (currentJar.isFile()) {
            classPath = currentJar.getName();
        } else {
            classPath = System.getProperty("java.class.path");
        }

        String javacPath = Runtime.JVM.compiler.get().getPath();
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
        classPath = radixBinPath+";"+radixLibPath+";"+classPath+";"+javacPath;
        command.add("-cp");
        command.add(classPath);

        command.add("-Dport="+rmiRegistry.getPort());
        command.add("-Dpath="+offshoot.getLocalPath());

        command.add(KernelBuilder.class.getCanonicalName());

        final ProcessBuilder builder = new ProcessBuilder(command);
        if (currentJar.isFile()) {
            builder.directory(currentJar.getParentFile());
        } else {
            builder.directory(currentJar);
        }

        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        rmiRegistry.registerService(BuildingNotifier.class.getTypeName(), new BuildingNotifier() {
            @Override
            public void error(Throwable ex) {
                errorRef.set(ex);
            }

            @Override
            public void description(String text) {
                setProgress(getProgress(), text);
            }

            @Override
            public void isPaused() {
                checkPaused();
            }
        });

        java.lang.Runtime.getRuntime().addShutdownHook(hook);
        Process process = builder.redirectErrorStream(true).start();
        addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                if (nextStatus.equals(Status.CANCELLED)) {
                    process.destroy();
                }
                if (nextStatus.isFinal()) {
                    try {
                        rmiRegistry.close();
                    } catch (IOException ignore) {}
                }
            }
        });

        final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.lines().iterator().forEachRemaining(s -> {/* ignore process output */ });
        process.waitFor();

        java.lang.Runtime.getRuntime().removeShutdownHook(hook);
        if (process.isAlive()) process.destroy();

        if (errorRef.get() == null && process.exitValue() > 0) {
            errorRef.set(new Exception(Language.get(BuildWC.class, "command@halted")));
        }

        if (errorRef.get() != null) {
            offshoot.setBuiltStatus(null);
            offshoot.model.commit(false);
            String message = MessageFormat.format(
                    "Build kernel [{0}/{1}] failed. Total time: {2}",
                    offshoot.getRepository().getPID(),
                    offshoot.getPID(),
                    DateUtils.formatElapsedTime(getDuration())
            );
            throw new ExecuteException(
                    Language.get(BuildWC.class, "command@failed"),
                    message.concat("\n").concat(Logger.stackTraceToString(BuildWC.getRootCause(errorRef.get())))
            );
        }
        return null;
    }

    @Override
    public void finished(Void t) {
        Logger.getLogger().info(MessageFormat.format(
                "Build kernel [{0}/{1}] {2}. Total time: {3}",
                offshoot.getRepository().getPID(),
                offshoot.getPID(),
                isCancelled() ? "canceled" : "finished",
                DateUtils.formatElapsedTime(getDuration())
        ));
    }
}
