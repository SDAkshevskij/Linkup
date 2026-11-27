#!/usr/bin/env python3
"""Package the user-local Linux installer and its application files."""
import argparse
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def package(output, source=ROOT, version_code=None):
    with zipfile.ZipFile(output, "x", zipfile.ZIP_DEFLATED) as archive:
        for folder in ("linux", "ui"):
            for path in sorted((source / folder).rglob("*")):
                if path.is_file() and "__pycache__" not in path.parts:
                    archive.write(path, path.relative_to(source))
        properties = (source / "version.properties").read_text()
        if version_code is not None:
            properties = "\n".join(
                f"versionCode={version_code}" if line.startswith("versionCode=") else line
                for line in properties.splitlines()
            ) + "\n"
        archive.writestr("version.properties", properties)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    package(args.output)
