package my.stepss.diagram;

import java.awt.geom.Rectangle2D;

/**
 * Where the window is looking, in the document's own coordinates.
 *
 * <p>Zoom and pan expressed as a rectangle rather than as a transform, because
 * the rectangle is what {@link SvgImage#render} wants: the renderer draws one
 * region at viewport resolution, so navigation is arithmetic on that region and
 * never a scaled bitmap.
 *
 * <p>No Swing, so {@code DiagramCheck} pins the arithmetic without a display.
 * Every navigation fault is an arithmetic fault, and this is the part of it
 * that can be held to account.
 *
 * <p>Zoom is expressed relative to Fit, so 1.0 is the whole drawing and 4.0 is
 * a quarter of it in each direction, whatever the document's own units are.
 */
public final class DiagramView {

    /** Below this the drawing is a speck and the only way back is Fit. */
    private static final double MIN_ZOOM = 0.05;

    /** Above this the numbers stop being readable for a different reason. */
    private static final double MAX_ZOOM = 50.0;

    private final Rectangle2D document;

    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private double zoom = 1.0;
    private double centreX;
    private double centreY;

    public DiagramView(Rectangle2D documentBounds) {
        this.document = (Rectangle2D) documentBounds.clone();
        this.centreX = document.getCenterX();
        this.centreY = document.getCenterY();
    }

    /** Tells the view how many pixels it is being drawn into. */
    public void setViewport(int width, int height) {
        this.viewportWidth = Math.max(1, width);
        this.viewportHeight = Math.max(1, height);
    }

    /** The whole drawing, centred. */
    public void fit() {
        zoom = 1.0;
        centreX = document.getCenterX();
        centreY = document.getCenterY();
    }

    /** Magnification relative to Fit: 1.0 is the whole drawing. */
    public double zoom() {
        return zoom;
    }

    /** Sets the magnification about the current centre, clamped. */
    public void setZoom(double value) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
        clampCentre();
    }

    /**
     * Multiplies the magnification, keeping the document point currently under
     * {@code (deviceX, deviceY)} under that same pixel afterwards.
     *
     * <p>The anchoring is the whole value of this method. Zooming about the
     * centre throws away whatever the user was pointing at, which on a diagram
     * the size of a transmission network means every zoom is followed by
     * hunting for the thing you were reading.
     */
    public void zoomAt(double factor, int deviceX, int deviceY) {
        Rectangle2D before = aoi();
        double anchorX = before.getX() + before.getWidth() * deviceX / viewportWidth;
        double anchorY = before.getY() + before.getHeight() * deviceY / viewportHeight;

        setZoom(zoom * factor);

        // Put the anchor back under the same pixel by moving the centre, which
        // is the only free variable once the zoom is fixed.
        Rectangle2D after = aoi();
        double landedX = after.getX() + after.getWidth() * deviceX / viewportWidth;
        double landedY = after.getY() + after.getHeight() * deviceY / viewportHeight;
        centreX += anchorX - landedX;
        centreY += anchorY - landedY;
        clampCentre();
    }

    /** Moves the view by a drag measured in pixels. */
    public void panBy(int deviceDx, int deviceDy) {
        Rectangle2D current = aoi();
        centreX -= deviceDx * current.getWidth() / viewportWidth;
        centreY -= deviceDy * current.getHeight() / viewportHeight;
        clampCentre();
    }

    /**
     * The region to render, in document coordinates, at the viewport's aspect
     * ratio.
     *
     * <p>The aspect match is not cosmetic. Batik takes a single uniform scale
     * from the two axes and centres the result when they disagree, so an area
     * of interest with a different shape from the output would be silently
     * repositioned by the renderer and this class's arithmetic would stop
     * describing what is on screen.
     */
    public Rectangle2D aoi() {
        // At Fit the region is the document grown on one axis until it matches
        // the viewport's shape, so the whole drawing is inside it either way.
        double fitWidth = document.getWidth();
        double fitHeight = document.getHeight();
        double viewAspect = (double) viewportWidth / viewportHeight;
        if (fitWidth / fitHeight < viewAspect) {
            fitWidth = fitHeight * viewAspect;
        } else {
            fitHeight = fitWidth / viewAspect;
        }
        double width = fitWidth / zoom;
        double height = fitHeight / zoom;
        return new Rectangle2D.Double(centreX - width / 2, centreY - height / 2,
                width, height);
    }

    /**
     * Keeps at least part of the drawing on screen.
     *
     * <p>Without it a drag can put the document entirely outside the viewport,
     * and the only way back is Fit, which a user who has just lost their
     * diagram has no reason to expect.
     */
    private void clampCentre() {
        Rectangle2D current = aoi();
        double halfW = current.getWidth() / 2;
        double halfH = current.getHeight() / 2;
        centreX = Math.max(document.getMinX() - halfW / 2,
                Math.min(document.getMaxX() + halfW / 2, centreX));
        centreY = Math.max(document.getMinY() - halfH / 2,
                Math.min(document.getMaxY() + halfH / 2, centreY));
    }
}
