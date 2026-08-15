#!/usr/bin/env bash
# Re-exports the application's marks from the stepss-docs SVG sources.
#
# The jar ships PNGs rather than SVGs, so that nothing has to render vectors at
# runtime and no SVG library is on the classpath. The cost of that choice is
# this step: whenever the artwork changes in stepss-docs, the renderings here go
# stale, and nothing in the build notices. Running this is the whole fix, and it
# is deliberately one command so there is no reason to do it by hand.
#
# Inkscape, not ImageMagick. 'convert -resize' rasterises an SVG at the size the
# document declares (295x100 for the lockup) and then scales the raster, so it
# produces a blurry enlargement of a thumbnail. Inkscape rasterises the vectors
# at the size asked for. This is not a preference; it is the difference between
# sharp and visibly soft.
#
# Usage: tools/refresh-marks.sh [path-to-stepss-docs/src/assets]
set -eu
cd "$(dirname "$0")/.."

ASSETS="${1:-../stepss-docs/src/assets}"
DEST="src/my/stepss"

if [ ! -d "$ASSETS" ]; then
    echo "Asset directory not found: $ASSETS" >&2
    echo "Pass the path to stepss-docs/src/assets as the first argument." >&2
    exit 1
fi
if ! command -v inkscape >/dev/null 2>&1; then
    echo "inkscape not found on PATH; it is what renders these correctly." >&2
    exit 1
fi
for name in logo-light logo-dark icon-light icon-dark; do
    if [ ! -f "$ASSETS/$name.svg" ]; then
        echo "Missing source: $ASSETS/$name.svg" >&2
        exit 1
    fi
done

# Widths the About lockup is drawn at: its layout width, and 2x and 3x of it for
# high density displays. Branding.LOCKUP_WIDTH and LOCKUP_SCALES must agree.
LOCKUP_WIDTHS="380 760 1140"
# Sizes handed to setIconImages. Branding.ICON_SIZES must agree.
ICON_SIZES="16 32 48 128"

for variant in light dark; do
    for w in $LOCKUP_WIDTHS; do
        inkscape --export-type=png \
                 --export-filename="$DEST/logo-$variant-$w.png" \
                 --export-width="$w" \
                 "$ASSETS/logo-$variant.svg" >/dev/null 2>&1
        echo "logo-$variant-$w.png"
    done
    for s in $ICON_SIZES; do
        inkscape --export-type=png \
                 --export-filename="$DEST/icon-$variant-$s.png" \
                 --export-width="$s" --export-height="$s" \
                 "$ASSETS/icon-$variant.svg" >/dev/null 2>&1
        echo "icon-$variant-$s.png"
    done
done

echo
echo "Re-exported into $DEST. 'ant jar' picks them up; tools/chrome-harness.sh"
echo "confirms every one of them still resolves."
