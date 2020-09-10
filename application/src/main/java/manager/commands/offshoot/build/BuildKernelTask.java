package manager.commands.offshoot.build;

import codex.log.Logger;
import codex.task.*;
import codex.utils.Language;
import codex.utils.Runtime;
import manager.commands.offshoot.BuildWC;
import manager.nodes.Offshoot;
import org.apache.tools.ant.util.DateUtils;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.UUID;
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
        UUID uuid = UUID.randomUUID();
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

        command.add("-Dport="+BuildWC.getPort());
        command.add("-Duuid="+uuid.toString());
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

        AtomicReference<Throwable> errorRef = new AtomicReference<>(null);
        BuildWC.getBuildNotifier().addListener(uuid, new IBuildingNotifier.IBuildListener() {
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
        if (currentJar.isFile()) {
            builder.directory(currentJar.getParentFile());
        } else {
            builder.directory(currentJar);
        }

        java.lang.Runtime.getRuntime().addShutdownHook(hook);
        Process process = builder.start();
        addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status prevStatus, Status nextStatus) {
                if (nextStatus.equals(Status.CANCELLED)) {
                    process.destroy();
                }
            }
        });
        process.waitFor();
        BuildWC.getBuildNotifier().removeListener(uuid);
        java.lang.Runtime.getRuntime().removeShutdownHook(hook);
        if (process.isAlive()) process.destroy();

        if (errorRef.get() != null) {
            offshoot.setBuiltStatus(null);
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {
                //
            }
            String message = MessageFormat.format(
                    "Build kernel [{0}/{1}] failed. Total time: {2}",
                    offshoot.getRepository().getPID(),
                    offshoot.getPID(),
                    DateUtils.formatElapsedTime(getDuration())
            );
            throw new ExecuteException(
                    MessageFormat.format(
                            Language.get(BuildWC.class, "command@seelog"),
                            offshoot.getLocalPath()+File.separator+"build-kernel.log"
                    ),
                    message.concat("\n").concat(Logger.stackTraceToString(errorRef.get()))
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
