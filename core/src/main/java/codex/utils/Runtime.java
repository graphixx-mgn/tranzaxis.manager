package codex.utils;

import codex.log.Logger;
import net.jcip.annotations.ThreadSafe;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.function.Supplier;

@ThreadSafe
public class Runtime {

    public static void systemInfo() {
        File javac = JVM.compiler.get();
        if (javac == null) {
            Logger.getLogger().warn("Java compiler not found");
        }
        Logger.getLogger().info(
                new StringJoiner("\n")
                        .add("Runtime environment:")
                        .add(" JVM:       {0} (ver.: {1})")
                        .add(" Java home: {2}")
                        .add(" Compiler:  {3}")
                        .add(" OS:        {4} (ver.: {5}, arch.: {6})")
                        .add(" Classpath: {7} (dev.mode: {8})")
                        .toString(),
                JVM.name.get(),
                JVM.version.get(),
                JVM.location.get(),
                javac == null ? "<not found>" : javac,
                OS.name.get(),
                OS.version.get(),
                OS.is64bit.get() ? "x64" : "x68",
                APP.jarFile.get(),
                APP.devMode.get() ? "ON" : "OFF"
        );
    }

    public static class JVM {
        public final static Supplier<String> name = () -> System.getProperty("java.runtime.name");
        public final static Supplier<String> version = () -> System.getProperty("java.vm.specification.version");
        public final static Supplier<File>   compiler = () -> {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler != null) {
                URL url = compiler.getClass().getProtectionDomain().getCodeSource().getLocation();
                try {
                    String urlDecoded = URLDecoder.decode(url.getPath(), "UTF-8");
                    return new File(urlDecoded);
                } catch (UnsupportedEncodingException ignore) {}
            }
            return null;
        };
        public final static Supplier<String> location = () -> System.getProperty("java.home");
    }


    public static class APP {
        public final static Supplier<Class> mainClass = () -> Caller.getInstance().getClassStack().stream()
                .reduce((first, second) -> second)
                .orElse(null);
        public final static Supplier<File> jarFile = () -> {
            try {
                return new File(
                        URLDecoder.decode(Runtime.class.getProtectionDomain().getCodeSource().getLocation().getFile(), "UTF-8")
                );
            } catch (UnsupportedEncodingException ignore) {}
            return null;
        };
        public final static Supplier<Boolean> devMode = () -> !jarFile.get().isFile();
    }


    public static class OS {
        public final static Supplier<String> name = () -> System.getProperty("os.name");
        public final static Supplier<String> version = () -> System.getProperty("os.version");

        public final static Supplier<Boolean> isWindows = () -> name.get().startsWith("Windows");
        public final static Supplier<Boolean> isLinux = () -> name.get().startsWith("Linux");
        public final static Supplier<Boolean> isMac = () -> name.get().startsWith("Mac");

        public final static Supplier<Boolean> win8 = () -> isWindows.get() && Float.parseFloat(version.get()) >= 6.0f;
        public final static Supplier<Boolean> win7 = () -> isWindows.get() && Float.parseFloat(version.get()) >= 6.1f;

        public final static Supplier<Boolean> is64bit = () -> System.getProperty("sun.cpu.isalist").contains("64");
    }
}
