package manager.commands.offshoot.build;


import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import static org.apache.batik.transcoder.SVGAbstractTranscoder.KEY_WIDTH;
import static org.apache.batik.transcoder.XMLAbstractTranscoder.KEY_DOCUMENT_ELEMENT;
import static org.apache.batik.transcoder.XMLAbstractTranscoder.KEY_DOCUMENT_ELEMENT_NAMESPACE_URI;
import static org.apache.batik.transcoder.XMLAbstractTranscoder.KEY_DOM_IMPLEMENTATION;
import static org.apache.batik.util.SVGConstants.SVG_NAMESPACE_URI;
import static org.apache.batik.util.SVGConstants.SVG_SVG_TAG;

class SvgImageLoader {

    static BufferedImage loadSvg(URL url, float width) throws IOException {
        SvgTranscoder transcoder = new SvgTranscoder();
        transcoder.setTranscodingHints(getHints(width));
        try {
            TranscoderInput input = new TranscoderInput(url.openStream());
            transcoder.transcode(input, null);
        } catch (TranscoderException e) {
            throw new IOException("Error parsing SVG file " + url, e);
        }
        return transcoder.getImage();
    }

    private static TranscodingHints getHints(float width) {
        TranscodingHints hints = new TranscodingHints();
        hints.put(KEY_DOM_IMPLEMENTATION, SVGDOMImplementation.getDOMImplementation());
        hints.put(KEY_DOCUMENT_ELEMENT_NAMESPACE_URI, SVG_NAMESPACE_URI);
        hints.put(KEY_DOCUMENT_ELEMENT, SVG_SVG_TAG);
        hints.put(KEY_WIDTH, width);
        return hints;
    }

    private static class SvgTranscoder extends ImageTranscoder {

        private BufferedImage image = null;

        @Override
        public BufferedImage createImage(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            return image;
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput out) {}

        BufferedImage getImage() {
            return image;
        }
    }
}
