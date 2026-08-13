package my.ramses.ssa;

/**
 * A PlotSink that emits SVG written to be edited afterwards: real text
 * elements, semantic groups, and a style block of named classes so changing
 * one hex value restyles every element of a kind at once.
 */
final class SvgSink implements PlotSink {

    private final StringBuilder body = new StringBuilder();
    private final int width;
    private final int height;
    private int openGroups;

    SvgSink(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void group(String id) {
        body.append("  <g id=\"").append(escape(id)).append("\">\n");
        openGroups++;
    }

    @Override
    public void endGroup() {
        if (openGroups > 0) {
            body.append("  </g>\n");
            openGroups--;
        }
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, null);
    }

    @Override
    public void dashedLine(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, "4,3");
    }

    private void emitLine(double x1, double y1, double x2, double y2, String cls,
            String dash) {
        body.append("    <line x1=\"").append(fmt(x1))
                .append("\" y1=\"").append(fmt(y1))
                .append("\" x2=\"").append(fmt(x2))
                .append("\" y2=\"").append(fmt(y2))
                .append("\" class=\"").append(escape(cls)).append('"');
        if (dash != null) {
            body.append(" stroke-dasharray=\"").append(dash).append('"');
        }
        body.append("/>\n");
    }

    @Override
    public void circle(double cx, double cy, double r, String cls) {
        body.append("    <circle cx=\"").append(fmt(cx))
                .append("\" cy=\"").append(fmt(cy))
                .append("\" r=\"").append(fmt(r))
                .append("\" class=\"").append(escape(cls)).append("\"/>\n");
    }

    @Override
    public void cross(double cx, double cy, double r, String cls) {
        emitLine(cx - r, cy - r, cx + r, cy + r, cls, null);
        emitLine(cx - r, cy + r, cx + r, cy - r, cls, null);
    }

    @Override
    public void arrow(double x1, double y1, double x2, double y2, String cls) {
        emitLine(x1, y1, x2, y2, cls, null);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double head = 7.0;
        double spread = Math.toRadians(22.0);
        emitLine(x2, y2, x2 - head * Math.cos(angle - spread),
                y2 - head * Math.sin(angle - spread), cls, null);
        emitLine(x2, y2, x2 - head * Math.cos(angle + spread),
                y2 - head * Math.sin(angle + spread), cls, null);
    }

    @Override
    public void text(double x, double y, String s, String anchor, String cls) {
        body.append("    <text x=\"").append(fmt(x))
                .append("\" y=\"").append(fmt(y))
                .append("\" text-anchor=\"").append(escape(anchor))
                .append("\" class=\"").append(escape(cls)).append("\">")
                .append(escape(s)).append("</text>\n");
    }

    String toSvg() {
        while (openGroups > 0) {
            endGroup();
        }
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
                .append("width=\"").append(width).append("\" ")
                .append("height=\"").append(height).append("\" ")
                .append("viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" ")
                .append("font-family=\"sans-serif\">\n");
        out.append("  <style>\n").append(buildStyleBlock()).append("  </style>\n");
        out.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        out.append(body);
        out.append("</svg>\n");
        return out.toString();
    }

    private static String buildStyleBlock() {
        StringBuilder sb = new StringBuilder();
        for (PlotStyle.Entry entry : PlotStyle.ENTRIES) {
            sb.append("    .");
            // Pad class name to 8 characters for alignment
            sb.append(String.format("%-8s", entry.cls));
            sb.append("{ ");

            if (entry.fontPx != null) {
                // Text class: use fill and font-size
                sb.append("fill: ").append(entry.hex).append("; ");
                sb.append("font-size: ").append(entry.fontPx).append("px; ");
                sb.append("stroke: none; ");
            } else {
                // Stroke class: use stroke, stroke-width, and fill
                sb.append("stroke: ").append(entry.hex).append("; ");
                sb.append("stroke-width: ");
                if (entry.width == (int) entry.width) {
                    sb.append((int) entry.width);
                } else {
                    sb.append(entry.width);
                }
                sb.append("; fill: none; ");
            }

            sb.append("}\n");
        }
        return sb.toString();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
