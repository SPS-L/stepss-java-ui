package my.ramses;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/**
 * The application's marks, in the two variants the documentation site carries.
 *
 * <p>Everything here is rasterised from the SVG sources in stepss-docs
 * {@code src/assets}, so the desktop application and the site are one identity
 * rather than two: {@code icon-{light,dark}.svg} becomes the square S, and
 * {@code logo-{light,dark}.svg} becomes the horizontal lockup in the About box.
 * The naming follows Starlight's, where <i>light</i> is the variant shown on a
 * light background, which is the one drawn in dark ink.
 *
 * <h2>Rasterise with Inkscape, not ImageMagick</h2>
 *
 * <p>This matters more than it sounds. {@code convert -resize 1140x logo.svg}
 * rasterises the document at its own declared size, 295x100 here, and then
 * upscales the result, so what reaches the jar is a four times enlargement of a
 * thumbnail: soft edges, mushy tagline, obviously wrong beside crisp text.
 * Inkscape rasterises the vectors at the requested size. Regenerate from
 * stepss-docs {@code src/assets} with:
 *
 * <pre>
 * inkscape --export-type=png --export-filename=logo-light-760.png \
 *          --export-width=760 logo-light.svg
 * inkscape --export-type=png --export-filename=icon-light-32.png \
 *          --export-width=32 --export-height=32 icon-light.svg
 * </pre>
 *
 * <h2>One file per display scale, rather than one file scaled</h2>
 *
 * <p>The lockup ships at {@value #LOCKUP_WIDTH}px and at two and three times
 * that, and is handed to Swing as a {@link BaseMultiResolutionImage}, so a
 * display at 2x is given the 760px rendering at exactly 1:1 rather than a
 * resampling of something else. Resampling at paint time was the previous
 * approach and it was the wrong trade: bilinear minification is soft at any
 * ratio that is not a clean halving, and it is soft on precisely the high
 * density screens where the artwork is most visible.
 *
 * <p>The dark lockup carries its own near-black panel, baked into the artwork
 * rather than added here; it is drawn that way on the documentation site too.
 */
final class Branding {

    /** Width the About lockup is laid out at, and the base variant's width. */
    static final int LOCKUP_WIDTH = 380;

    /** Display scales the lockup ships a rendering for. */
    private static final int[] LOCKUP_SCALES = {1, 2, 3};

    /** Rasterised sizes of the square mark, smallest first. */
    private static final int[] ICON_SIZES = {16, 32, 48, 128};

    private Branding() {
    }

    /**
     * The window and taskbar icons, every size, for the given theme.
     *
     * <p>Handing Swing the whole set rather than one image lets each consumer
     * pick: a 16px title bar takes the 16, an alt-tab switcher takes the 128,
     * and neither has to resample.
     */
    static List<Image> windowIcons(boolean dark) {
        List<Image> images = new ArrayList<Image>(ICON_SIZES.length);
        for (int size : ICON_SIZES) {
            BufferedImage image = read("icon-" + variant(dark) + "-" + size + ".png");
            if (image != null) {
                images.add(image);
            }
        }
        return images;
    }

    /**
     * The About box lockup, {@value #LOCKUP_WIDTH}px wide, carrying its higher
     * density renderings with it. Null when the artwork is missing, which is
     * what {@code ChromeCheck} exists to catch.
     */
    static Icon logo(boolean dark) {
        List<Image> variants = new ArrayList<Image>(LOCKUP_SCALES.length);
        for (int scale : LOCKUP_SCALES) {
            BufferedImage image = read("logo-" + variant(dark)
                    + "-" + (LOCKUP_WIDTH * scale) + ".png");
            if (image != null) {
                variants.add(image);
            }
        }
        if (variants.isEmpty()) {
            return null;
        }
        // The first variant is the base, and its size is the logical size the
        // icon reports; the rest are only ever chosen by the drawing pipeline.
        Image base = variants.get(0);
        int height = Math.round(LOCKUP_WIDTH * base.getHeight(null)
                / (float) base.getWidth(null));
        return new LockupIcon(
                new BaseMultiResolutionImage(variants.toArray(new Image[0])),
                LOCKUP_WIDTH, height);
    }

    private static String variant(boolean dark) {
        return dark ? "dark" : "light";
    }

    /**
     * Every resource this class expects to find, for {@code ChromeCheck} to
     * confirm each one resolves. Generated from the same constants the loaders
     * use, so a size added above cannot be left unchecked, and a file dropped
     * from the jar cannot pass.
     *
     * <p>A missing density is the quiet one worth guarding. If the 2x lockup
     * disappeared, nothing would fail: the pipeline would fall back to enlarging
     * the 1x, which is exactly the softness this arrangement exists to avoid,
     * and it would only be visible to someone on a high density display.
     */
    static List<String> requiredResources() {
        List<String> names = new ArrayList<String>();
        for (boolean dark : new boolean[]{false, true}) {
            for (int size : ICON_SIZES) {
                names.add("icon-" + variant(dark) + "-" + size + ".png");
            }
            for (int scale : LOCKUP_SCALES) {
                names.add("logo-" + variant(dark) + "-" + (LOCKUP_WIDTH * scale) + ".png");
            }
        }
        return names;
    }

    /** Whether {@code resource} is present beside this class. */
    static boolean has(String resource) {
        return Branding.class.getResource(resource) != null;
    }

    private static BufferedImage read(String resource) {
        URL url = Branding.class.getResource(resource);
        if (url == null) {
            return null;
        }
        try {
            return ImageIO.read(url);
        } catch (IOException ex) {
            // A missing mark is a cosmetic fault, never a reason not to start.
            java.util.logging.Logger.getLogger(Branding.class.getName())
                    .log(java.util.logging.Level.WARNING, "Could not read " + resource, ex);
            return null;
        }
    }

    /**
     * Draws a multi-resolution image at its logical size. No interpolation hint
     * is set, on purpose: the point of shipping variants is that the pipeline
     * finds one at the device size and copies it, and a hint here would only
     * describe how to blur it if it ever failed to.
     */
    private static final class LockupIcon implements Icon {

        private final Image image;
        private final int width;
        private final int height;

        LockupIcon(Image image, int width, int height) {
            this.image = image;
            this.width = width;
            this.height = height;
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.drawImage(image, x, y, width, height, null);
        }
    }
}
