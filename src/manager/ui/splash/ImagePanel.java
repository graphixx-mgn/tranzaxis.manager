/*
com.sixlegs.png - Java package to read and display PNG images
Copyright (C) 1998-2006 Chris Nokleberg

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
*/

package manager.ui.splash;

import java.awt.*;
;import java.awt.image.BufferedImage;
import javax.swing.*;

class ImagePanel extends JPanel {
    
    public static final int STRETCH_NONE = 0;
    public static final int STRETCH_FULL = 1;
    public static final int STRETCH_PRESERVE = 2;

    private int stretchMode = STRETCH_NONE;
    private BufferedImage image;

    public ImagePanel() {
        this(null);
    }

    public ImagePanel(BufferedImage image) {
        this.image = image;
        setLayout(new BorderLayout());
    }

    public int getStretchMode() {
        return stretchMode;
    }
    
    public void setStretchMode(int stretchMode) {
        this.stretchMode = stretchMode;
        repaint();
    }
    
    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            Dimension size = getSize();
            boolean skipStretch = true;
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            switch (stretchMode) {
            case STRETCH_PRESERVE:
                skipStretch = size.width == imageWidth || size.height == imageHeight;
                break;
            case STRETCH_FULL:
                skipStretch = size.width == imageWidth && size.height == imageHeight;
            }
            
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            if (skipStretch) {
                int x = (size.width - imageWidth) / 2;
                int y = (size.height - imageHeight) / 2;
                g2.drawImage(image, x, y, this);
            } else if (stretchMode == STRETCH_PRESERVE) {
                double scale = Math.min((double)size.width / imageWidth, (double)size.height / imageHeight);
                int w = (int)(imageWidth * scale);
                int h = (int)(imageHeight * scale);
                int x = (size.width - w) / 2;
                int y = (size.height - h) / 2;
                g2.drawImage(image, x, y, x + w, y + h, 0, 0, imageWidth, imageHeight, this);
            } else {
                g2.drawImage(image, 0, 0, size.width, size.height, this);
            }
        }
    }
}