#!/usr/bin/env python3
"""
Minimal entity generator for this template.

Usage:
  python3 generate_entity.py module_name field1:type field2:type
Example:
  python3 generate_entity.py product name:string price:bigdecimal active:boolean
"""

from pathlib import Path
import sys

PACKAGE_ROOT = "com.abzed.template"
SRC = Path("src/main/java")

TYPE_MAP = {
    "string": "String",
    "int": "Integer",
    "integer": "Integer",
    "long": "Long",
    "bool": "Boolean",
    "boolean": "Boolean",
    "bigdecimal": "java.math.BigDecimal",
    "uuid": "java.util.UUID",
}


def to_class_name(name: str) -> str:
    return "".join(part.capitalize() for part in name.split("_"))


def resolve_type(raw: str):
    mapped = TYPE_MAP.get(raw.lower(), raw)
    if "." in mapped:
        return mapped.split(".")[-1], mapped
    return mapped, None


def main():
    if len(sys.argv) < 3:
        print("Usage: python3 generate_entity.py module_name field:type ...")
        sys.exit(1)

    module = sys.argv[1].lower()
    entity = to_class_name(module)

    fields = []
    imports = set(["jakarta.persistence.*", "lombok.Getter", "lombok.Setter", "java.util.UUID"])

    for token in sys.argv[2:]:
        if ":" not in token:
            raise ValueError(f"Invalid field format: {token}. Expected field:type")
        name, raw_type = token.split(":", 1)
        type_name, fqcn = resolve_type(raw_type)
        fields.append((name, type_name))
        if fqcn:
            imports.add(fqcn)

    package = f"{PACKAGE_ROOT}.modules.{module}"
    target_dir = SRC / Path(*package.split("."))
    target_dir.mkdir(parents=True, exist_ok=True)

    lines = [
        f"package {package};",
        "",
        *[f"import {imp};" for imp in sorted(imports)],
        "",
        "@Getter",
        "@Setter",
        "@Entity",
        f"@Table(name = \"{module}s\")",
        f"public class {entity} {{",
        "",
        "    @Id",
        "    @GeneratedValue(strategy = GenerationType.UUID)",
        "    private UUID id;",
        "",
    ]

    for name, type_name in fields:
        lines.append(f"    private {type_name} {name};")

    lines.append("}")

    out = target_dir / f"{entity}.java"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Generated: {out}")


if __name__ == "__main__":
    main()
