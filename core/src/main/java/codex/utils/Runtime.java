package codex.utils;

import codex.log.Logger;
import codex.type.IComplexType;
import net.jcip.annotations.ThreadSafe;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        public final static Supplier<List<JavaInfo>> list = () -> {
            try {
                return RuntimeStreamer.execute(new String[] {
                        OS.isWindows.get() ? "where" : "which",
                        "java"
                }).stream()
                        .map(JavaInfo::build)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
                return Collections.emptyList();
            }
        };
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

        public final static Supplier<Boolean> is64bit = () -> IComplexType.coalesce(
                System.getProperty("sun.cpu.isalist"),
                System.getProperty("os.arch")
        ).contains("64");
    }


    public static class JavaInfo {
        private static Pattern GET_NAME_VERSION = Pattern.compile("(.*) \\(build (.*)-.*\\)");
        public final String  path;
        public final String  name;
        public final String  version;

        private static JavaInfo build(String javaPath) {
            try {
                return new JavaInfo(javaPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private JavaInfo(String javaPath) throws IOException{
            this.path = javaPath;
            List<String> versionInfo = RuntimeStreamer.execute( new String[] { javaPath, "-version" } );
            String  fullName = versionInfo.get(1);
            Matcher nameMatcher = GET_NAME_VERSION.matcher(fullName);
            if (nameMatcher.find()) {
                this.name    = nameMatcher.group(1);
                this.version = nameMatcher.group(2);
            } else {
                throw new IOException("Unable to parse JMV name");
            }
        }
    }


    private static class RuntimeStreamer extends Thread {
        InputStream  is;
        List<String> lines;

        RuntimeStreamer(InputStream is) {
            this.is = is;
            this.lines = new LinkedList<>();
        }

        synchronized List<String> contents() {
            return this.lines;
        }

        synchronized public void run() {
            try (
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br  = new BufferedReader(isr);
            ) {
                String line;
                while ((line = br.readLine()) != null) {
                    this.lines.add(line);
                }
            } catch (IOException e) {
                // Do nothing
            }
        }

        public static List<String> execute(String[] cmdArray) throws IOException {
            try {
                java.lang.Runtime runtime = java.lang.Runtime.getRuntime();
                Process proc = runtime.exec(cmdArray);
                RuntimeStreamer outputStreamer = new RuntimeStreamer(proc.getInputStream());
                RuntimeStreamer errorStreamer  = new RuntimeStreamer(proc.getErrorStream());
                outputStreamer.start();
                errorStreamer.start();
                proc.waitFor();
                return Stream.concat(
                        outputStreamer.contents().stream(),
                        errorStreamer.contents().stream()
                ).collect(Collectors.toList());
            } catch (Throwable t) {
                t.printStackTrace();
                throw new IOException(t.getMessage());
            }
        }
        public static List<String> execute(String cmd) throws IOException {
            String[] cmdArray = { cmd };
            return RuntimeStreamer.execute(cmdArray);
        }
    }
}
