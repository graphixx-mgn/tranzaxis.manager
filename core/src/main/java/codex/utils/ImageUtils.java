package codex.utils;

import codex.editor.IEditor;
import codex.log.Logger;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;

public class ImageUtils {
    
    public static ImageIcon getByPath(String path) {
        path = path.replaceFirst("^/", "");
        List<Class> stack = Caller.getInstance().getClassStack().stream()
                .filter(aClass -> aClass != ImageUtils.class)
                .collect(Collectors.toList());
        return getByPath(stack.get(0), path);
    }

    public static ImageIcon getByPath(Class callerClass, String path) {
        path = path.replaceFirst("^/", "");
        URL classURL = ((URLClassLoader) callerClass.getClassLoader()).getURLs()[0];
        URL imageURL = null;
        try {
            Enumeration<URL> resources = callerClass.getClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL nextURL = resources.nextElement();
                if (nextURL.getFile().contains(classURL.getFile())) {
                    imageURL = nextURL;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (imageURL == null) {
            imageURL = callerClass.getClassLoader().getResource(path);
        }
        if (imageURL != null) {
            return new ImageIcon(imageURL);
        } else {
            Logger.getLogger().error(MessageFormat.format("Image ''{0}'' not found", path), new Exception());
            return new ImageIcon();
        }
    }

    public static ImageIcon resize(ImageIcon icon, float scale) {
        int newSize = (int) (icon.getIconHeight() * scale);
        return resize(icon, newSize, newSize);
    }
    
    public static ImageIcon resize(ImageIcon icon, int width, int height) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        BufferedImage dimg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dimg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.drawImage(icon.getImage(), 0, 0, width, height, 0, 0, w, h, null);
        g.dispose();
        return new ImageIcon(dimg);
    }

    public static ImageIcon rotate(ImageIcon icon, double angle) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        BufferedImage dimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dimg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.rotate(Math.toRadians(angle), w/2, h/2);
        g.drawImage(icon.getImage(), 0, 0, w, h, 0, 0, w, h, null);
        g.dispose();
        return new ImageIcon(dimg);
    }
    
    public static ImageIcon grayscale(ImageIcon icon) {
        // https://www.dyclassroom.com/image-processing-project/how-to-convert-a-color-image-into-grayscale-image-in-java
        ImageObserver observer = icon.getImageObserver();
        Image srcImage = icon.getImage();
        int width  = srcImage.getWidth(observer);
        int height = srcImage.getHeight(observer);
        
        final BufferedImage destImage = new BufferedImage( 
                width, 
                height, 
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D gr = destImage.createGraphics();
        gr.drawImage(srcImage, 0, 0, null);
       
        //convert to grayscale
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                int p = destImage.getRGB(x,y);

                int a = (p>>24)&0xff;
                int r = (p>>16)&0xff;
                int g = (p>>8)&0xff;
                int b = p&0xff;

                //calculate average
                int avg = (r+g+b)/3;

                //replace RGB value with avg
                p = (a<<24) | (avg<<16) | (avg<<8) | avg;
                destImage.setRGB(x, y, p);
            }
        }
        return new ImageIcon(destImage);
    }
    
    public static ImageIcon combine(ImageIcon bgIcon, ImageIcon fgIcon) {
        return combine(bgIcon, fgIcon, SwingConstants.CENTER);
    }

    public static ImageIcon combine(ImageIcon bgIcon, ImageIcon fgIcon, int position) {
        Image srcImage = bgIcon.getImage();
        int width  = Math.max(bgIcon.getIconWidth(), fgIcon.getIconWidth());
        int height = Math.max(bgIcon.getIconHeight(), fgIcon.getIconHeight());

        int fgPosX, fgPosY;
        switch (position) {
            case SwingConstants.NORTH_WEST:
                fgPosX = 0;
                fgPosY = 0;
                break;
            case SwingConstants.NORTH_EAST:
                fgPosX = width - fgIcon.getIconWidth();
                fgPosY = 0;
                break;
            case SwingConstants.SOUTH_WEST:
                fgPosX = 0;
                fgPosY = height - fgIcon.getIconHeight();
                break;
            case SwingConstants.SOUTH_EAST:
                fgPosX = width - fgIcon.getIconWidth();
                fgPosY = height - fgIcon.getIconHeight();
                break;
            default:
                fgPosX = width / 2 - fgIcon.getIconWidth() / 2;
                fgPosY = height / 2 - fgIcon.getIconHeight() / 2;
        }

        final BufferedImage combinedImage = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = combinedImage.createGraphics();
        g.drawImage(
                srcImage,
                width / 2 - bgIcon.getIconWidth() / 2,
                height / 2 - bgIcon.getIconHeight() / 2,
                null
        );
        g.drawImage(
                fgIcon.getImage(),
                fgPosX,
                fgPosY,
                null
        );
        return new ImageIcon(combinedImage);
    }

    public static ImageIcon createBadge(String text, Color bgColor, Color fgColor) {
        return createBadge(text, bgColor, fgColor, 0);
    }

    public static ImageIcon createBadge(String text, Color bgColor, Color fgColor, int borderSize) {
        Font font = new Font("Arial", Font.BOLD, IEditor.FONT_BOLD.getSize());

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gm = image.createGraphics();
        FontMetrics fm = gm.getFontMetrics(font);

        int textWidth  = fm.stringWidth(text);
        int textHeight = fm.getAscent() + fm.getDescent();

        BufferedImage bufferedImage = new BufferedImage(textHeight+textWidth+borderSize*2, textHeight+borderSize*2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) bufferedImage.getGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.Src);

        if (borderSize > 0) {
            g.setColor(fgColor);
            g.fill(new Arc2D.Double(0, 0, textHeight + borderSize * 2, textHeight + borderSize * 2, 90, 180, Arc2D.PIE));
            g.fill(new Rectangle2D.Double(textHeight / 2, 0, textWidth, textHeight + borderSize * 2));
            g.fill(new Arc2D.Double(textWidth - borderSize, 0, textHeight + borderSize * 2, textHeight + borderSize * 2, 270, 180, Arc2D.PIE));
        }
        g.setColor(bgColor);
        g.fill(new Arc2D.Double(borderSize, borderSize, textHeight, textHeight, 90, 180, Arc2D.PIE));
        g.fill(new Rectangle2D.Double(textHeight/2, borderSize, textWidth, textHeight));
        g.fill(new Arc2D.Double(textWidth, borderSize, textHeight, textHeight, 270, 180, Arc2D.PIE));

        g.setColor(fgColor);
        g.setFont(font);
        g.drawString(text, textHeight/2, fm.getAscent()+borderSize);
        return new ImageIcon(bufferedImage);
    }

    public static String toBase64(ImageIcon imageIcon) {
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


    public static class HTMLToolKit extends HTMLEditorKit {
        private static HTMLFactory factory = null;

        @Override
        public ViewFactory getViewFactory() {
            if (factory == null) {
                factory = new HTMLFactory() {
                    @Override
                    public View create(Element elem) {
                        AttributeSet attrs = elem.getAttributes();
                        Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
                        Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
                        if (o instanceof HTML.Tag) {
                            HTML.Tag kind = (HTML.Tag) o;
                            if (kind == HTML.Tag.IMG) {
                                return new BASE64ImageView(elem);
                            }
                        }
                        return super.create(elem);
                    }
                };
            }
            return factory;
        }
    }


    private static class BASE64ImageView extends ImageView {

        BASE64ImageView(Element element) {
            super(element);
            populateImage();
        }

        private void populateImage() {
            Dictionary<URL, Image> cache = (Dictionary<URL, Image>) getDocument().getProperty("imageCache");
            if (cache == null) {
                cache = new Hashtable<>();
                getDocument().putProperty("imageCache", cache);
            }
            cache.put(getImageURL(), loadImage());
        }

        private Image loadImage() {
            String b64 = getBASE64Image();
            BufferedImage newImage = null;
            ByteArrayInputStream bais = null;
            try {
                bais = new ByteArrayInputStream(Base64.getDecoder().decode(b64.getBytes()));
                newImage = ImageIO.read(bais);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
            return newImage;
        }

        @Override
        public URL getImageURL() {
            String src = (String) getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
            if (isBase64Encoded(src)) {
                try {
                    return new URL("file:"+src);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
            return super.getImageURL();
        }

        private boolean isBase64Encoded(String src) {
            return src != null && src.contains("base64,");
        }

        private String getBASE64Image() {
            String src = (String) getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
            if (!isBase64Encoded(src)) {
                return null;
            }
            return src.substring(src.indexOf("base64,") + 7);
        }
    }

    public static String hexColor(Color color) {
        StringBuilder hex = new StringBuilder(Integer.toHexString(color.getRGB() & 0xffffff));
        while (hex.length() < 6){
            hex.insert(0, "0");
        }
        hex.insert(0, "#");
        return hex.toString();
    }
    
}
