package manager.commands.offshoot.build;


import codex.log.Logger;
import codex.task.*;
import codex.utils.Language;
import manager.commands.offshoot.BuildWC;
import manager.nodes.Offshoot;
import manager.type.BuildStatus;
import manager.upgrade.UpgradeService;
import org.apache.tools.ant.util.DateUtils;
import org.radixware.kernel.common.check.RadixProblem;

import javax.swing.*;
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
        super(Language.get(BuildWC.class.getSimpleName(), "command@kernel"));
        this.offshoot = offshoot;
    }

    @Override
    public boolean isPauseable() {
        return true;
    }

    @Override
    public Void execute() throws Exception {
        UUID uuid = UUID.randomUUID();
        final File currentJar = UpgradeService.getCurrentJar();

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

        Runtime.getRuntime().addShutdownHook(hook);
        Process process = builder.start();
        addListener(new ITaskListener() {
            @Override
            public void statusChanged(ITask task, Status status) {
                if (status.equals(Status.CANCELLED)) {
                    process.destroy();
                }
            }
        });
        process.waitFor();
        BuildWC.getBuildNotifier().removeListener(uuid);
        Runtime.getRuntime().removeShutdownHook(hook);
        if (process.isAlive()) process.destroy();

        if (errorRef.get() != null) {
            offshoot.setBuiltStatus(new BuildStatus(offshoot.getWorkingCopyRevision(false).getNumber(), true));
            try {
                offshoot.model.commit(false);
            } catch (Exception e) {}
            String message = MessageFormat.format(
                    "BUILD KERNEL [{0}] failed. Total time: {1}",
                    offshoot.getLocalPath(), DateUtils.formatElapsedTime(getDuration())
            );
            throw new ExecuteException(
                    MessageFormat.format(
                            Language.get(BuildWC.class.getSimpleName(), "command@seelog"),
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
                "BUILD SOURCE [{0}] {2}. Total time: {1}",
                offshoot.getLocalPath(), DateUtils.formatElapsedTime(getDuration()),
                isCancelled() ? "canceled" : "finished"
        ));
    }
}
