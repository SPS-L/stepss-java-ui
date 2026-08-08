"""CLI entry point: python3 -m tools.ci <subcommand>"""

import sys

from . import bump

COMMANDS = {
    "bump": bump.main,
}


def main(argv):
    if len(argv) < 2 or argv[1] not in COMMANDS:
        sys.stderr.write(
            "Usage: python3 -m tools.ci <%s> [options]\n" % "|".join(sorted(COMMANDS))
        )
        return 2
    return COMMANDS[argv[1]](argv[2:])


if __name__ == "__main__":
    sys.exit(main(sys.argv))
