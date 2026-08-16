import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Composites the splash card from the light lockup. Run once, and again
 * whenever the lockup changes:
 *
 *   java tools/MakeSplash.java
 *
 * Writes src/my/stepss/splash-460.png and splash-460@2x.png. The card is
 * OPAQUE and square-cornered on purpose: a rounded card needs per-pixel alpha,
 * and a splash window without translucency support draws the corners black.
 *
 * Only the light card is generated. A dark launch repaints the whole thing at
 * runtime, so shipping a second file would be a second thing to keep in step.
 */
public final class MakeSplash {

    private static final int W = 460;
    private static final int H = 250;

    public static void main(String[] args) throws Exception {
        write(1, "src/my/stepss/logo-light-380.png", "src/my/stepss/splash-460.png");
        write(2, "src/my/stepss/logo-light-760.png", "src/my/stepss/splash-460@2x.png");
        System.out.println("splash artwork written");
    }

    private static void write(int scale, String lockupPath, String out) throws Exception {
        BufferedImage lockup = ImageIO.read(new File(lockupPath));
        BufferedImage card = new BufferedImage(W * scale, H * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W * scale, H * scale);
        g.setColor(new Color(0xD8DEE4));
        g.drawRect(0, 0, W * scale - 1, H * scale - 1);
        // Centred horizontally, 28px from the top at 1x. The runtime overpaint
        // uses the same numbers, so the dark card lands the lockup in exactly
        // the same place as this one.
        g.drawImage(lockup, (W * scale - lockup.getWidth()) / 2, 28 * scale, null);
        g.dispose();
        ImageIO.write(card, "png", new File(out));
    }
}
