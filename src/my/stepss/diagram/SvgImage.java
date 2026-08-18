package my.stepss.diagram;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.dom.AbstractDocument;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.svg.SVGDocument;

/**
 * One SVG, parsed once and rendered on demand.
 *
 * <p>Holds the parsed document rather than the file, deliberately. The window
 * showing a run's diagram must keep showing that run's diagram: the next Run
 * Power Flow deletes and rewrites {@code in_diagram.svg}, and a class that
 * re-read the file on every zoom would quietly change what an already open
 * window displayed. This is the same reasoning {@code SsaResultsWindow} records
 * for holding its own parsed results, and it is what makes comparing two runs
 * side by side true rather than nearly true.
 *
 * <p>Rendering goes through {@link ImageTranscoder} rather than
 * {@code JSVGCanvas}. The canvas starts an update-manager thread, follows
 * external references through its document loader and wires up the script
 * bridge; for a static drawing all three are surface with no purpose, and two
 * of them are the shape of CVE-2022-44729 and CVE-2022-44730. Nothing here
 * animates, scripts or fetches.
 *
 * <p>{@link #render} takes an area of interest in the document's own user
 * space, which is what keeps this affordable on a large network: the image is
 * always the size of the viewport, so an eightfold zoom costs the same as
 * fitting the whole diagram rather than eight times as much.
 */
public final class SvgImage {

    private final SVGDocument document;
    private final File source;
    private final Rectangle2D bounds;

    private SvgImage(SVGDocument document, File source, Rectangle2D bounds) {
        this.document = document;
        this.source = source;
        this.bounds = bounds;
    }

    /**
     * Parses {@code file}.
     *
     * @throws IOException if it cannot be read or is not SVG. Raising is the
     * point: a renderer that answered a malformed file with a blank image would
     * put an empty window on screen with nothing saying why.
     */
    public static SvgImage load(File file) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = lenientFactory(parser);
        SVGDocument document;
        try {
            document = (SVGDocument) factory.createDocument(file.toURI().toString());
        } catch (IOException ex) {
            throw new IOException("Could not read " + file.getName()
                    + " as an SVG file: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            // Batik reports a document that is not markup by throwing from the
            // SAX layer, which arrives here unchecked.
            throw new IOException(file.getName() + " is not a readable SVG file: "
                    + ex.getMessage(), ex);
        }
        return new SvgImage(document, file, readBounds(document, file));
    }

    /**
     * A document factory that tolerates elements the SVG DOM does not know.
     *
     * <p>Batik's strict DOM throws {@code DOMException} for any unrecognised
     * local name in the SVG namespace, and refuses the entire document over
     * it. Drawing tools emit exactly that: the bundled 6-bus example carries
     * {@code <version>1.0</version>} inside {@code <desc>}, left there by
     * WinFIG, which inherits the SVG default namespace and is not an SVG
     * element. Every browser ignores it. A viewer of files it did not author
     * has to do the same, or the first real diagram a user loads is refused
     * for a reason that has nothing to do with the drawing.
     *
     * <p>An unknown element is remapped into the null namespace, which makes a
     * generic DOM element. GVT has no bridge for one, so it contributes no
     * graphics and the rest of the drawing renders unchanged. Verified against
     * the real 6bus.svg: identical output to a copy with the element deleted.
     *
     * <p>The implementation cannot be installed by assigning the factory's
     * field. {@code SAXSVGDocumentFactory} overrides
     * {@code getDOMImplementation(String)} to return the strict singleton and
     * never consults the field (SAXSVGDocumentFactory.java:323), so the
     * override below is the hook that works.
     *
     * <p>This does not weaken the refusal of a genuinely broken file. Markup
     * that is not XML fails inside the SAX parser, before any element is
     * created, and never reaches this path.
     */
    private static SAXSVGDocumentFactory lenientFactory(String parser) {
        final SVGDOMImplementation lenient = new SVGDOMImplementation() {
            @Override
            public Element createElementNS(AbstractDocument document,
                    String namespaceURI, String qualifiedName) {
                try {
                    return super.createElementNS(document, namespaceURI, qualifiedName);
                } catch (DOMException unknownElement) {
                    return super.createElementNS(document, null, qualifiedName);
                }
            }
        };
        return new SAXSVGDocumentFactory(parser) {
            @Override
            public DOMImplementation getDOMImplementation(String version) {
                return lenient;
            }
        };
    }

    /**
     * The document's own coordinate extent: its {@code viewBox} when it has
     * one, its width and height otherwise.
     *
     * <p>Read from the attributes rather than from a built scene graph, so
     * loading costs one parse. A document with neither is refused, because
     * everything downstream divides by these numbers.
     */
    private static Rectangle2D readBounds(SVGDocument document, File file)
            throws IOException {
        String viewBox = document.getRootElement().getAttribute("viewBox");
        if (viewBox != null && !viewBox.trim().isEmpty()) {
            String[] parts = viewBox.trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    return new Rectangle2D.Double(
                            Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                } catch (NumberFormatException notNumbers) {
                    throw new IOException(file.getName()
                            + " has a viewBox that is not four numbers: " + viewBox);
                }
            }
        }
        double width = length(document.getRootElement().getAttribute("width"));
        double height = length(document.getRootElement().getAttribute("height"));
        if (width <= 0 || height <= 0) {
            throw new IOException(file.getName() + " declares no viewBox and no"
                    + " usable width and height, so there is nothing to say how"
                    + " large the drawing is.");
        }
        return new Rectangle2D.Double(0, 0, width, height);
    }

    /**
     * A length attribute's numeric part, unit ignored.
     *
     * <p>Only reached for a document with no viewBox, where the units decide
     * the aspect ratio and nothing else: the value is used as a ratio against
     * the other axis, so "9.9in" by "5.3in" and "9.9" by "5.3" give the same
     * shape. A document mixing units across the two axes would be wrong here,
     * and is not something a drawing tool produces.
     */
    private static double length(String value) {
        if (value == null) {
            return 0;
        }
        String digits = value.trim().replaceAll("[^0-9.\\-].*$", "");
        try {
            return digits.isEmpty() ? 0 : Double.parseDouble(digits);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** The document's coordinate extent. Never null, never empty. */
    public Rectangle2D documentBounds() {
        return (Rectangle2D) bounds.clone();
    }

    /**
     * Renders one rectangle of the document at a given pixel size.
     *
     * <p>{@code aoi} should carry the same aspect ratio as the output. Batik
     * takes a single uniform scale from the two axes and centres the result
     * when they disagree, so matching them keeps this class's arithmetic the
     * only thing deciding what appears.
     *
     * @param aoi    the region, in the document's own user space
     * @param width  the output width in pixels, at least 1
     * @param height the output height in pixels, at least 1
     */
    public BufferedImage render(Rectangle2D aoi, int width, int height)
            throws IOException {
        Capture capture = new Capture();
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH,
                Float.valueOf(Math.max(1, width)));
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT,
                Float.valueOf(Math.max(1, height)));
        capture.addTranscodingHint(SVGAbstractTranscoder.KEY_AOI, aoi);
        try {
            capture.transcode(new TranscoderInput(document), new TranscoderOutput());
        } catch (TranscoderException ex) {
            throw new IOException("Could not draw " + source.getName() + ": "
                    + ex.getMessage(), ex);
        }
        return capture.image;
    }

    /**
     * Renders the whole document at a given width, aspect preserved.
     *
     * <p>What Save as PNG uses. A saved figure goes into a report, so it is the
     * whole drawing at a generous size rather than whatever the window happened
     * to be showing: zoom is a reading aid here, not a crop tool.
     */
    public BufferedImage renderWhole(int width) throws IOException {
        int height = (int) Math.round(width * bounds.getHeight() / bounds.getWidth());
        return render(documentBounds(), width, Math.max(1, height));
    }

    /** Copies the SVG this was loaded from to {@code dest}, for Save as SVG. */
    public void copyTo(File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * An {@link ImageTranscoder} that keeps the image instead of encoding it.
     *
     * <p>This is what makes {@code batik-codec} unnecessary: that module exists
     * to write PNG bytes, and the JDK's own {@code ImageIO} does that when the
     * user asks for a file. Nothing here writes to the {@code TranscoderOutput}
     * at all.
     */
    private static final class Capture extends ImageTranscoder {

        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) {
            this.image = img;
        }
    }
}
