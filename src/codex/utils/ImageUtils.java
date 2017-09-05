package codex.utils;

import codex.log.Logger;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ImageObserver;
import javax.swing.ImageIcon;

public class ImageUtils {
    
    public static ImageIcon getByPath(String path) {
        String className = new Exception().getStackTrace()[1].getClassName();
        try {
            return new ImageIcon(Class.forName(className).getResource(path));
        } catch (ClassNotFoundException e) {
            Logger.getLogger().error("Unexpected error", e);
        }
        return null;
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
        ImageObserver observer = icon.getImageObserver();
        Image srcImage = icon.getImage();
        int width   = srcImage.getHeight(observer);
        int height  = srcImage.getHeight(observer);
        
        BufferedImage destImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        destImage.createGraphics().drawImage(srcImage, 0, 0, width, height, null);

        ColorSpace grayColorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorConvertOp op = new ColorConvertOp(grayColorSpace, destImage.getColorModel().getColorSpace(), null);
        op.filter(destImage, destImage);
        return new ImageIcon(destImage);
    }
    
    public static ImageIcon combine(ImageIcon bgIcon, ImageIcon fgIcon) {
        ImageObserver observer = bgIcon.getImageObserver();
        Image srcImage = bgIcon.getImage();
        int width   = srcImage.getHeight(observer);
        int height  = srcImage.getHeight(observer);

        final BufferedImage combinedImage = new BufferedImage( 
                width, 
                width, 
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g = combinedImage.createGraphics();
        g.drawImage(srcImage, 0, 0, null);
        g.drawImage(fgIcon.getImage(), 0, 0, null);
        return new ImageIcon(combinedImage);
    }
    
}
