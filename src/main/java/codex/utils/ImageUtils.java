package codex.utils;

import codex.log.Logger;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.ImageIcon;

public class ImageUtils {
    
    public static ImageIcon getByPath(String path) {
        path = path.replaceFirst("^/", "");
        List<Class> stack = Caller.getInstance().getClassStack().stream()
                .filter(aClass -> aClass != ImageUtils.class)
                .collect(Collectors.toList());
        URL resource = stack.get(0).getClassLoader().getResource(path);
        if (resource != null) {
            return new ImageIcon(resource);
        } else {
            Logger.getLogger().error("Image ''{0}'' not found", path);
        }
        return new ImageIcon();
    }

    public static ImageIcon getByPath(Class callerClass, String path) {
        path = path.replaceFirst("^/", "");
        URL resource = callerClass.getClassLoader().getResource(path);
        if (resource != null) {
            return new ImageIcon(resource);
        } else {
            Logger.getLogger().error("Image ''{0}'' not found", path);
            return new ImageIcon();
        }
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
        Image srcImage = bgIcon.getImage();
        int width  = bgIcon.getIconWidth();
        int height = bgIcon.getIconHeight();

        final BufferedImage combinedImage = new BufferedImage( 
                width, 
                height, 
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = combinedImage.createGraphics();
        g.drawImage(srcImage, 0, 0, null);
        g.drawImage(fgIcon.getImage(), 0, 0, null);
        return new ImageIcon(combinedImage);
    }
    
}
