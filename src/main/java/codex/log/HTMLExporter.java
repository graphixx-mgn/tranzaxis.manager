package codex.log;

import codex.component.dialog.Dialog;
import codex.context.IContext;
import codex.mask.FileMask;
import codex.model.ParamModel;
import codex.presentation.EditorPage;
import codex.supplier.IDataSupplier;
import codex.type.FilePath;
import codex.utils.ImageUtils;
import codex.utils.Language;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

class HTMLExporter {

    private final static String PARAM_PATH = "path";
    private final static String USER_HOME  = javax.swing.filechooser.FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
    private final static String DEF_FILE   = "eventlog.htm";
    private final static SimpleDateFormat DATE_FORMAT  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
    private static final Map<String, String> CTX_ICONS = Logger.getContexts().stream()
            .collect(Collectors.toMap(
                    Logger::getContextId,
                    ctxClass -> toBase64(Logger.getContextIcon(ctxClass))
            ));
    private static final Map<String, String> CTX_NAMES = Logger.getContexts().stream()
            .collect(Collectors.toMap(
                    Logger::getContextId,
                    ctxClass -> ctxClass.getAnnotation(IContext.Definition.class).name()
            ));

    private final static ThreadLocal<List<Map<String, String>>> DATA = new ThreadLocal<>();

    static void export(EventLogSupplier supplier) throws IDataSupplier.LoadDataException {
        List<Map<String, String>> data = supplier.getNext();
        Path defaultPath = Paths.get(USER_HOME, DEF_FILE);
        ParamModel paramModel = new ParamModel();

        paramModel.addProperty(
                PARAM_PATH,
                new FilePath(defaultPath).setMask(new FileMask(
                        new FileNameExtensionFilter("HTML", ".html")
                )),
                true
        );

        new Dialog(
                null,
                ImageUtils.getByPath("/images/export.png"),
                Language.get("dialog@title"),
                new EditorPage(paramModel),
                event -> {
                    if (event.getID() == Dialog.OK) {
                        try {
                            writeFile(((FilePath) paramModel.getParameters().get(PARAM_PATH)).getValue(), data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                },
                Dialog.Default.BTN_OK,
                Dialog.Default.BTN_CANCEL
        ) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(650, super.getPreferredSize().getSize().height);
            }
        }.setVisible(true);
    }

    private static void writeFile(Path path, List<Map<String, String>> data) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
        }

        DATA.set(data);
        FileOutputStream fileStream = new FileOutputStream(path.toFile());
        fileStream.write(prepareHtml().getBytes());
    }

    private static String prepareHtml() {
        return MessageFormat.format(
               Language.get("html"),
               prepareHead(),
               prepareBody()
        );
    }

    private static String prepareHead() {
        return MessageFormat.format(
               Language.get("head"),
               prepareCSS("/log.css"),
               prepareJavaScript("/jquery-3.2.1.min.js"),
               prepareJavaScript("/log.js")
        );
    }

    private static String prepareCSS(String resource) {
        final InputStream cssResource = HTMLExporter.class.getResourceAsStream(resource);
        return MessageFormat.format(
               Language.get("css"),
               new Scanner(cssResource, "UTF-8").useDelimiter("\\A").next(),
               DATA.get().stream()
                        .map(logRecord -> {
                                String[] contexts = logRecord.get("CONTEXT").split(",", -1);
                                return contexts[contexts.length-1];
                        })
                        .distinct()
                        .map(ctxID -> MessageFormat.format(
                                Language.get("icon.ext"),
                                ctxID.replace(".", "_"),
                                CTX_ICONS.get(ctxID/*.replace(".", "_")*/)
                        )).collect(Collectors.joining("\n"))

        );
    }

    private static String prepareJavaScript(String resource) {
        final InputStream jsResource = HTMLExporter.class.getResourceAsStream(resource);
        return MessageFormat.format(
               Language.get("script"),
               new Scanner(jsResource, "UTF-8").useDelimiter("\\A").next()
        );
    }

    private static String prepareBody() {
        return MessageFormat.format(
               Language.get("body"),
               prepareToolbar(),
               prepareTable()
        );
    }

    private static String prepareToolbar() {
        return MessageFormat.format(
               Language.get("tools"),
               prepareHeader(),
               prepareLevelFilter(),
               prepareContextFilter()
        );
    }

    private static String prepareHeader() {
        return MessageFormat.format(
               Language.get("header"),
               DATE_FORMAT.format(new Date()),
               System.getProperty("user.name")
        );
    }

    private static String prepareLevelFilter() {
        return Language.get("filter@level");
    }

    private static String prepareContextFilter() {
        return MessageFormat.format(
               Language.get("filter@context"),
               DATA.get().stream()
                        .map(logRecord -> {
                            String[] contexts = logRecord.get("CONTEXT").split(",", -1);
                            return contexts[contexts.length-1];
                        })
                        .distinct()
                        .map(ctxID -> MessageFormat.format(
                               Language.get("filter@context.control"),
                               ctxID.replace(".", "_"),
                               CTX_NAMES.get(ctxID)
                        )).collect(Collectors.joining("\n"))
        );
    }

    private static String prepareTable() {
        return MessageFormat.format(
               Language.get("table"),
               String.join("\n", prepareData())
        );
    }

    private static List<String> prepareData() {
        List<Map<String, String>> data = DATA.get();
        return data.stream()
                .map(logRecord -> {
                    String[] contexts = logRecord.get("CONTEXT").split(",", -1);
                    return MessageFormat.format(
                            Language.get("record"),
                            logRecord.get("LEVEL"),
                            logRecord.get("TIME"),
                            contexts[contexts.length-1].replace(".", "_"),
                            prepareMessage(logRecord)
                    );
                })
                .collect(Collectors.toList());
    }

    private static String prepareMessage(Map<String, String> record) {
        String text = escape(record.get("MESSAGE"))
                .replaceAll("\\n", "<br/>")
                .replaceAll("\\t", "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;")
                .replaceAll("\\*\\s", "&bull;&nbsp;")
                .replaceAll("\\s", "&nbsp;");
        boolean multiLine = text.contains("<br/>");
        boolean hasStack  = !record.get("STACK").isEmpty();
        return MessageFormat.format(
               Language.get("message"),
               multiLine ? text.substring(0, text.indexOf("<br/>")).concat(" [...]") : text,
               multiLine ? MessageFormat.format(Language.get("details"), text) : "",
               hasStack  ? MessageFormat.format(
                       Language.get("stack"),
                       record.get("STACK").replaceAll("\\n", "<br/>&nbsp;&nbsp;&nbsp;")
               ) : ""
        );
    }

    private static String escape(String data) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
                out.append("&#");
                out.append((int) c);
                out.append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String toBase64(ImageIcon imageIcon) {
        BufferedImage image = new BufferedImage(
                imageIcon.getIconWidth(),
                imageIcon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics g = image.createGraphics();
        imageIcon.paintIcon(null, g, 0, 0);
        g.dispose();

        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", b);
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] imageInByte = b.toByteArray();
        return new String(Base64.getEncoder().encode(imageInByte));
    }
}
