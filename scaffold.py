#!/usr/bin/env python3
"""
Scaffold a new project from this template — a clean, renamed local copy.

Usage:
  python scaffold.py <target-dir> [options]

Examples:
  python scaffold.py ../acme-portal --package com.acme.portal
  python scaffold.py ../billing-service --package com.acme.billing --name "Billing Service"
  python scaffold.py my-app --package com.example.myapp --no-git

Options:
  --package    Java base package (e.g. com.acme.portal). Prompted if omitted.
  --group      Maven groupId. Defaults to the base package.
  --artifact   Maven artifactId / slug. Defaults to the target directory name.
  --name       Human-readable display name. Defaults from the artifact.
  --no-git     Do not run `git init` / initial commit in the new project.
  --force      Allow a non-empty target directory.

The script copies the template it lives in (excluding build output, .git, IDE
files, and itself), so just run it from this repo against a new directory.
"""

import argparse
import base64
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

SOURCE_ROOT = Path(__file__).resolve().parent
SCRIPT_NAME = Path(__file__).name

# Names never copied into the generated project.
EXCLUDE_NAMES = {
    ".git", "target", ".idea", "node_modules", "HELP.md",
    ".env", ".env.local", SCRIPT_NAME,
}
EXCLUDE_SUFFIXES = {".iml", ".class", ".jar"}

# Only these files get their contents rewritten (token replacement).
TEXT_SUFFIXES = {
    ".java", ".xml", ".yaml", ".yml", ".md", ".sql", ".properties",
    ".gitattributes", ".dockerignore", ".example",
}
TEXT_NAMES = {"Dockerfile", "docker-compose.yml", ".gitlab-ci.yml", "ci.yml", ".env.example"}


def fail(message):
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def slugify(value):
    value = value.strip().lower()
    value = re.sub(r"[\s_]+", "-", value)
    value = re.sub(r"[^a-z0-9-]", "", value)
    value = re.sub(r"-+", "-", value).strip("-")
    return value


def pascal_case(slug):
    return "".join(part.capitalize() for part in re.split(r"[-_]", slug) if part)


def titleize(slug):
    return " ".join(part.capitalize() for part in re.split(r"[-_]", slug) if part)


def validate_package(package):
    parts = package.split(".")
    pattern = re.compile(r"^[a-z][a-z0-9_]*$")
    if len(parts) < 2 or not all(pattern.match(p) for p in parts):
        fail(f"invalid Java package: '{package}' (use lowercase dotted segments, e.g. com.acme.app)")


def is_text_file(path):
    return path.suffix in TEXT_SUFFIXES or path.name in TEXT_NAMES


def copy_template(target):
    def ignore(directory, names):
        ignored = set()
        for name in names:
            full = Path(directory) / name
            if name in EXCLUDE_NAMES or full.suffix in EXCLUDE_SUFFIXES:
                ignored.add(name)
        return ignored

    shutil.copytree(SOURCE_ROOT, target, ignore=ignore)


def move_package_tree(target, base_dir, new_package):
    old_pkg = base_dir / "com" / "abzed" / "template"
    if not old_pkg.is_dir():
        return
    new_pkg = base_dir.joinpath(*new_package.split("."))
    new_pkg.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(old_pkg), str(new_pkg))
    # Prune now-empty old ancestors.
    for ancestor in (base_dir / "com" / "abzed", base_dir / "com"):
        if ancestor.is_dir() and not any(ancestor.iterdir()):
            ancestor.rmdir()


def build_replacements(package, group, artifact, db_name, app_class, display_name):
    # Order matters: most specific first.
    return [
        ("com.abzed.template", package),
        ("MainJavaTemplateApplication", app_class + "Application"),
        ("Main Java Template", display_name),
        ("main-java-template", artifact),
        ("main_java_template", db_name),
        ("main-template", artifact),
    ]


def rewrite_contents(target, replacements, package, group):
    for path in target.rglob("*"):
        if not path.is_file() or not is_text_file(path):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        original = text
        for old, new in replacements:
            text = text.replace(old, new)
        # If the Maven groupId should differ from the base package, fix the pom.
        if path.name == "pom.xml" and group != package:
            text = text.replace(f"<groupId>{package}</groupId>", f"<groupId>{group}</groupId>", 1)
        if text != original:
            path.write_text(text, encoding="utf-8")


def rename_app_classes(target, app_class):
    for tests in (False, True):
        suffix = "ApplicationTests.java" if tests else "Application.java"
        old_name = "MainJavaTemplate" + suffix
        new_name = app_class + suffix
        for path in target.rglob(old_name):
            path.rename(path.with_name(new_name))


def seed_env(target):
    example = target / ".env.example"
    if not example.is_file():
        return
    text = example.read_text(encoding="utf-8")
    access = base64.b64encode(os.urandom(32)).decode()
    refresh = base64.b64encode(os.urandom(32)).decode()
    text = text.replace("APP_JWT_ACCESS_SECRET=REPLACE_WITH_BASE64_SECRET", f"APP_JWT_ACCESS_SECRET={access}")
    text = text.replace("APP_JWT_REFRESH_SECRET=REPLACE_WITH_BASE64_SECRET", f"APP_JWT_REFRESH_SECRET={refresh}")
    (target / ".env").write_text(text, encoding="utf-8")


def git_init(target):
    try:
        subprocess.run(["git", "init", "-q"], cwd=target, check=True)
        subprocess.run(["git", "add", "."], cwd=target, check=True)
        subprocess.run(
            ["git", "commit", "-q", "-m", "Initial commit from main-java-template"],
            cwd=target, check=True,
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def main():
    parser = argparse.ArgumentParser(add_help=True, description="Scaffold a new project from this template.")
    parser.add_argument("target", help="Target directory for the new project.")
    parser.add_argument("--package", help="Java base package (e.g. com.acme.app).")
    parser.add_argument("--group", help="Maven groupId (defaults to the base package).")
    parser.add_argument("--artifact", help="Maven artifactId / slug (defaults to the directory name).")
    parser.add_argument("--name", help="Display name (defaults from the artifact).")
    parser.add_argument("--no-git", action="store_true", help="Skip git init.")
    parser.add_argument("--force", action="store_true", help="Allow a non-empty target directory.")
    args = parser.parse_args()

    target = Path(args.target).resolve()
    if target == SOURCE_ROOT:
        fail("target must be a different directory from the template")
    if target.exists() and any(target.iterdir()) and not args.force:
        fail(f"target '{target}' exists and is not empty (use --force to override)")

    artifact = slugify(args.artifact or target.name)
    if not artifact:
        fail("could not derive a valid artifact name; pass --artifact")

    package = args.package
    if not package:
        if sys.stdin.isatty():
            package = input(f"Java base package [com.example.{artifact.replace('-', '')}]: ").strip()
        package = package or f"com.example.{artifact.replace('-', '')}"
    validate_package(package)

    group = args.group or package
    display_name = args.name or titleize(artifact)
    app_class = pascal_case(artifact)
    if not app_class or not app_class[0].isalpha():
        fail(f"could not derive a valid main-class name from '{artifact}'")
    db_name = artifact.replace("-", "_")

    print(f"Scaffolding '{display_name}' -> {target}")
    print(f"  package:  {package}")
    print(f"  groupId:  {group}")
    print(f"  artifact: {artifact}")
    print(f"  main:     {app_class}Application")

    if target.exists() and args.force:
        # Copy into an existing (possibly empty) directory.
        for item in SOURCE_ROOT.iterdir():
            if item.name in EXCLUDE_NAMES or item.suffix in EXCLUDE_SUFFIXES:
                continue
            dest = target / item.name
            if item.is_dir():
                shutil.copytree(item, dest, dirs_exist_ok=True)
            else:
                shutil.copy2(item, dest)
    else:
        copy_template(target)

    move_package_tree(target, target / "src" / "main" / "java", package)
    move_package_tree(target, target / "src" / "test" / "java", package)

    replacements = build_replacements(package, group, artifact, db_name, app_class, display_name)
    rewrite_contents(target, replacements, package, group)
    rename_app_classes(target, app_class)
    seed_env(target)

    committed = False
    if not args.no_git:
        committed = git_init(target)

    print("\nDone. Next steps:")
    print(f"  cd {target}")
    print("  docker compose up -d postgres mailpit   # start dependencies")
    print("  ./mvnw verify                            # build + test")
    print("  ./mvnw spring-boot:run                   # run")
    print("\nA .env with freshly generated JWT secrets was created (gitignored).")
    if not args.no_git and not committed:
        print("Note: git was not initialized (git not found or commit failed).")


if __name__ == "__main__":
    main()
