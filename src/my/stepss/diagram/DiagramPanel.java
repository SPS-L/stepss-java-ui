package my.stepss.diagram;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The diagram, drawn, with the mouse and keys wired to {@link DiagramView}.
 *
 * <p>The one piece of real machinery here is the split between the cheap answer
 * and the correct one. Re-rendering per mouse-move event would lurch on a
 * complicated network, so a drag or a wheel burst paints the last image
 * transformed, which is immediate, and a coalescing timer issues one fresh
 * render when the gesture settles. The picture tracks the mouse and sharpens a
 * moment later, which is what decides whether this is pleasant to use on
 * anything larger than a teaching case.
 */
public final class DiagramPanel extends JComponent {

    /** Long enough to coalesce a gesture, short enough not to feel like lag. */
    private static final int SETTLE_MS = 120;

    private final SvgImage image;
    private final DiagramView view;
    private final Timer settle;

    /** The last good render, and the view rectangle it was drawn for. */
    private BufferedImage rendered;
    private java.awt.geom.Rectangle2D renderedFor;

    private Point dragFrom;

    public DiagramPanel(SvgImage image) {
        this.image = image;
        this.view = new DiagramView(image.documentBounds());
        // event.getSource() rather than the settle field itself: javac treats
        // any reference to a blank final field from within its own
        // initializer as unassigned, even inside a lambda that will not run
        // until long after the constructor returns, so "settle.stop()" here
        // fails to compile. The event's source is the same Timer.
        this.settle = new Timer(SETTLE_MS, event -> {
            ((Timer) event.getSource()).stop();
            rerender();
        });
        settle.setRepeats(false);

        setOpaque(true);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        wireMouse();
        wireKeys();
        // Without this, growing the window leaves the previous image scaled up
        // to the new size and blurry: nothing else asks for a fresh render,
        // because rerender() is reached only from a gesture or the first paint.
        // schedule() coalesces through the settle timer, so a drag-resize
        // produces one render rather than one per pixel.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                schedule();
            }
        });
    }

    /** The document being shown, for the window's save buttons. */
    public SvgImage image() {
        return image;
    }

    /** Shows the whole drawing. */
    public void fit() {
        view.fit();
        schedule();
    }

    /** Multiplies the magnification about the viewport centre. */
    public void zoomBy(double factor) {
        view.zoomAt(factor, getWidth() / 2, getHeight() / 2);
        schedule();
    }

    private void wireMouse() {
        java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                requestFocusInWindow();
                dragFrom = event.getPoint();
                if (event.getClickCount() == 2) {
                    fit();
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                dragFrom = null;
                schedule();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent event) {
                if (dragFrom == null) {
                    return;
                }
                view.panBy(event.getX() - dragFrom.x, event.getY() - dragFrom.y);
                dragFrom = event.getPoint();
                schedule();
            }

            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent event) {
                double factor = Math.pow(1.15, -event.getPreciseWheelRotation());
                view.zoomAt(factor, event.getX(), event.getY());
                schedule();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void wireKeys() {
        int step = 40;
        bind("LEFT", () -> view.panBy(step, 0));
        bind("RIGHT", () -> view.panBy(-step, 0));
        bind("UP", () -> view.panBy(0, step));
        bind("DOWN", () -> view.panBy(0, -step));
        bind("PLUS", () -> view.zoomAt(1.25, getWidth() / 2, getHeight() / 2));
        bind("EQUALS", () -> view.zoomAt(1.25, getWidth() / 2, getHeight() / 2));
        bind("MINUS", () -> view.zoomAt(0.8, getWidth() / 2, getHeight() / 2));
        bind("0", view::fit);
    }

    private void bind(String key, Runnable action) {
        Object name = "diagram." + key;
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
                schedule();
            }
        });
    }

    /** Repaints now from what we have, and asks for a fresh render shortly. */
    private void schedule() {
        repaint();
        settle.restart();
    }

    private void rerender() {
        view.setViewport(getWidth(), getHeight());
        java.awt.geom.Rectangle2D wanted = view.aoi();
        try {
            rendered = image.render(wanted, getWidth(), getHeight());
            renderedFor = wanted;
        } catch (IOException ex) {
            // Keep the last good picture rather than blanking the window. The
            // document parsed once already, so a failure here is transient
            // (memory, most likely) and the previous image is still true of
            // where the view was.
            java.util.logging.Logger.getLogger(DiagramPanel.class.getName())
                    .log(java.util.logging.Level.WARNING, "Diagram render failed", ex);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = UIManager.getColor("Panel.background");
        g.setColor(background == null ? Color.WHITE : background);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (rendered == null || renderedFor == null) {
            view.setViewport(getWidth(), getHeight());
            rerender();
            if (rendered == null) {
                return;
            }
        }

        // Where the rendered region now sits, given where the view has moved
        // to since. Equal rectangles give an identity transform, which is the
        // settled case; during a gesture this is the cheap approximation that
        // keeps the picture under the mouse.
        view.setViewport(getWidth(), getHeight());
        java.awt.geom.Rectangle2D now = view.aoi();
        double scale = renderedFor.getWidth() / now.getWidth();
        double x = (renderedFor.getX() - now.getX()) / now.getWidth() * getWidth();
        double y = (renderedFor.getY() - now.getY()) / now.getHeight() * getHeight();

        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.translate(x, y);
            g2.scale(scale, scale);
            g2.drawImage(rendered, 0, 0, null);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return new java.awt.Dimension(900, 560);
    }
}
