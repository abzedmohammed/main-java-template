#!/usr/bin/env python3
"""
Generate a full CRUD module for this template.

Usage:
  python generate_entity.py <module> <field:type> ... [options]

Example:
  python generate_entity.py user name:string email:email phone:phone active:boolean --prefix usr
  python generate_entity.py school name:string code:code email:email --prefix sch --soft-delete

Produces (under the project's base package):
  <module>/<Entity>.java
  <module>/<Entity>Repository.java
  <module>/<Entity>Service.java
  <module>/<Entity>Controller.java
  <module>/dto/Save<Entity>Request.java
  <module>/dto/<Entity>ListRequest.java
  <module>/dto/<Entity>Response.java
  <module>/mapper/<Entity>Mapper.java
  resources/db/migration/V<n>__create_<plural>_table.sql

All endpoints return the standard ApiResponse envelope (consistent with the
rest of the template and the React frontend contract).

API shape (no GET/PUT/PATCH):
  POST   /api/<plural>/save     create when no id, update when id present
  POST   /api/<plural>/list     pagination + search ({ start, limit, search })
  DELETE /api/<plural>/{id}

Options:
  --prefix       3-letter field prefix (e.g. usr, sch). Defaults to first 3 letters.
  --soft-delete  Add <prefix>Deleted / <prefix>DeletedAt and soft-delete logic.
  --base-package Override the detected base package.
  --force        Overwrite an existing module.
"""

import argparse
import re
import sys
from pathlib import Path
from string import Template

ROOT = Path(__file__).resolve().parent
JAVA_MAIN = ROOT / "src" / "main" / "java"
MIGRATIONS = ROOT / "src" / "main" / "resources" / "db" / "migration"

# type -> (javaType, sqlType, fqImport|None, searchable, [validationAnnotations])
TYPES = {
    "string":        ("String", "varchar(255)", None, True, ["@NotBlank"]),
    "text":          ("String", "text", None, True, []),
    "email":         ("String", "varchar(255)", None, True, ["@NotBlank", "@Email"]),
    "phone":         ("String", "varchar(255)", None, True, []),
    "code":          ("String", "varchar(255)", None, True, []),
    "url":           ("String", "varchar(255)", None, True, []),
    "slug":          ("String", "varchar(255)", None, True, []),
    "int":           ("Integer", "integer", None, False, []),
    "integer":       ("Integer", "integer", None, False, []),
    "long":          ("Long", "bigint", None, False, []),
    "bool":          ("Boolean", "boolean", None, False, []),
    "boolean":       ("Boolean", "boolean", None, False, []),
    "bigdecimal":    ("BigDecimal", "numeric(38,2)", "java.math.BigDecimal", False, []),
    "decimal":       ("BigDecimal", "numeric(38,2)", "java.math.BigDecimal", False, []),
    "double":        ("Double", "double precision", None, False, []),
    "float":         ("Float", "real", None, False, []),
    "date":          ("LocalDate", "date", "java.time.LocalDate", False, []),
    "localdate":     ("LocalDate", "date", "java.time.LocalDate", False, []),
    "datetime":      ("LocalDateTime", "timestamp(6)", "java.time.LocalDateTime", False, []),
    "localdatetime": ("LocalDateTime", "timestamp(6)", "java.time.LocalDateTime", False, []),
    "uuid":          ("UUID", "uuid", "java.util.UUID", False, []),
}

VALIDATION_IMPORTS = {
    "@NotBlank": "jakarta.validation.constraints.NotBlank",
    "@Email": "jakarta.validation.constraints.Email",
}

LOMBOK_DTO = [
    "lombok.AllArgsConstructor",
    "lombok.Builder",
    "lombok.Getter",
    "lombok.NoArgsConstructor",
    "lombok.Setter",
]


def fail(msg):
    print(f"error: {msg}", file=sys.stderr)
    sys.exit(1)


def cap(s):
    return s[0].upper() + s[1:] if s else s


def pascal(name):
    return "".join(p.capitalize() for p in re.split(r"[-_]", name) if p)


def camel_to_snake(s):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", s).lower()


def pluralize(word):
    if word.endswith("y") and (len(word) < 2 or word[-2] not in "aeiou"):
        return word[:-1] + "ies"
    if word.endswith(("s", "x", "z", "ch", "sh")):
        return word + "es"
    return word + "s"


def detect_base_package(override):
    if override:
        return override
    if JAVA_MAIN.is_dir():
        for path in JAVA_MAIN.rglob("*Application.java"):
            text = path.read_text(encoding="utf-8")
            if "@SpringBootApplication" in text:
                m = re.search(r"package\s+([\w.]+);", text)
                if m:
                    return m.group(1)
    fail("could not detect base package; pass --base-package")


def imports_block(imports):
    return "\n".join(f"import {i};" for i in sorted(set(imports)))


def render(template, ctx):
    return Template(template).substitute(ctx)


def write(path, content, force):
    if path.exists() and not force:
        fail(f"{path} already exists (use --force to overwrite)")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"  generated {path.relative_to(ROOT)}")


# --------------------------------------------------------------------------- #
# Templates
# --------------------------------------------------------------------------- #

ENTITY_T = """package $module_pkg;

$imports

@Getter
@Setter
@Entity
@Table(name = "$plural")
public class $Entity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID $idField;

$field_decls
    @Column(nullable = false)
    private LocalDateTime $createdField;

    private LocalDateTime $updatedField;
$soft_decls}
"""

SAVE_REQUEST_T = """package $dto_pkg;

$imports

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Save${Entity}Request {

    private UUID $idField;

$fields
}
"""

LIST_REQUEST_T = """package $dto_pkg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ${Entity}ListRequest {

    @Builder.Default
    private Integer start = 0;

    @Builder.Default
    private Integer limit = 10;

    @Builder.Default
    private String search = "";
}
"""

RESPONSE_T = """package $dto_pkg;

$imports

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ${Entity}Response {

    private UUID $idField;

$fields
    private LocalDateTime $createdField;

    private LocalDateTime $updatedField;
}
"""

MAPPER_T = """package $mapper_pkg;

$imports

@Component
public class ${Entity}Mapper {

    public $Entity toEntity(Save${Entity}Request request) {
        $Entity entity = new $Entity();
$set_lines
        return entity;
    }

    public void updateEntity($Entity entity, Save${Entity}Request request) {
$set_lines
    }

    public ${Entity}Response toResponse($Entity entity) {
        return ${Entity}Response.builder()
                .$idField(entity.get${IdFieldCap}())
$builder_lines
                .$createdField(entity.get${CreatedCap}())
                .$updatedField(entity.get${UpdatedCap}())
                .build();
    }
}
"""

REPOSITORY_T = """package $module_pkg;

$imports

public interface ${Entity}Repository extends JpaRepository<$Entity, UUID> {
$search_method}
"""

SEARCH_METHOD_T = """
    @Query(\"\"\"
            SELECT $alias FROM $Entity $alias
            WHERE $conditions
            \"\"\")
    Page<$Entity> search${PluralCap}(@Param("search") String search, Pageable pageable);
"""

SERVICE_T = """package $module_pkg;

$imports

@Service
@RequiredArgsConstructor
public class ${Entity}Service {

    private final ${Entity}Repository ${entity}Repository;
    private final ${Entity}Mapper ${entity}Mapper;

    @Transactional
    public ${Entity}Response save${Entity}(Save${Entity}Request request) {
        $Entity entity;
        if (request.get${IdFieldCap}() != null) {
            entity = ${entity}Repository.findById(request.get${IdFieldCap}())
                    .orElseThrow(() -> new NotFoundException("$Entity not found with id: " + request.get${IdFieldCap}()));
            ${entity}Mapper.updateEntity(entity, request);
        } else {
            entity = ${entity}Mapper.toEntity(request);
        }

        entity.set${UpdatedCap}(LocalDateTime.now());
        if (entity.get${CreatedCap}() == null) {
            entity.set${CreatedCap}(LocalDateTime.now());
        }

        $Entity saved = ${entity}Repository.save(entity);
        return ${entity}Mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<${Entity}Response> list${PluralCap}(${Entity}ListRequest request) {
        int start = request.getStart() != null ? request.getStart() : 0;
        int limit = request.getLimit() != null ? request.getLimit() : 10;
        String search = request.getSearch() != null ? request.getSearch().trim() : "";

        if (start < 0) {
            start = 0;
        }
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100;
        }

        Pageable pageable = PageRequest.of(start / limit, limit, Sort.by(Sort.Direction.DESC, "$idField"));

$page_block

        return page.map(${entity}Mapper::toResponse);
    }

    @Transactional
    public void delete${Entity}(UUID id) {
$delete_block
    }
}
"""

CONTROLLER_T = """package $module_pkg;

$imports

@RestController
@RequestMapping("/api/$plural")
@RequiredArgsConstructor
public class ${Entity}Controller {

    private final ${Entity}Service ${entity}Service;

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<${Entity}Response>> save${Entity}(@Valid @RequestBody Save${Entity}Request request) {
        return ResponseEntity.ok(ApiResponse.success("$Entity saved", ${entity}Service.save${Entity}(request)));
    }

    @PostMapping("/list")
    public ResponseEntity<ApiResponse<List<${Entity}Response>>> list${PluralCap}(@RequestBody ${Entity}ListRequest request) {
        Page<${Entity}Response> page = ${entity}Service.list${PluralCap}(request);
        return ResponseEntity.ok(ApiResponse.page("$Entity list", page.getContent(), page.getTotalElements()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> delete${Entity}(@PathVariable UUID id) {
        ${entity}Service.delete${Entity}(id);
        return ResponseEntity.ok(ApiResponse.success("$Entity deleted", null));
    }
}
"""

MIGRATION_T = """-- Generated CRUD table for $Entity.
create table $plural (
    $idCol uuid not null,
$col_lines
    $createdCol timestamp(6) not null,
    $updatedCol timestamp(6),
$soft_cols    primary key ($idCol)
);
"""


def main():
    parser = argparse.ArgumentParser(description="Generate a CRUD module from this template.")
    parser.add_argument("module", help="Singular entity name, e.g. product, school.")
    parser.add_argument("fields", nargs="*", help="field:type pairs, e.g. name:string price:bigdecimal")
    parser.add_argument("--prefix", help="Field prefix, e.g. usr, sch (default: first 3 letters).")
    parser.add_argument("--soft-delete", action="store_true", help="Generate soft-delete fields and logic.")
    parser.add_argument("--base-package", help="Override the detected base package.")
    parser.add_argument("--force", action="store_true", help="Overwrite existing files.")
    args = parser.parse_args()

    base = detect_base_package(args.base_package)
    module = re.sub(r"[^a-zA-Z0-9_]", "", args.module).lower()
    if not module:
        fail("invalid module name")

    Entity = pascal(module)
    entity = Entity[0].lower() + Entity[1:]
    alias = Entity[0].lower()
    plural = pluralize(entity)
    plural_cap = cap(plural)
    prefix = (args.prefix or module[:3]).lower()
    if not re.match(r"^[a-z]+$", prefix):
        fail("prefix must be lowercase letters only")

    id_field = prefix + "Id"
    created_field = prefix + "CreatedAt"
    updated_field = prefix + "UpdatedAt"
    deleted_field = prefix + "Deleted"
    deleted_at_field = prefix + "DeletedAt"

    # Parse fields
    fields = []
    for token in args.fields:
        if ":" not in token:
            fail(f"invalid field '{token}', expected name:type")
        raw, ftype = token.split(":", 1)
        ftype = ftype.lower()
        if ftype not in TYPES:
            fail(f"unknown type '{ftype}' (known: {', '.join(sorted(TYPES))})")
        java_type, sql_type, fq, searchable, anns = TYPES[ftype]
        fname = prefix + cap(re.sub(r"[^a-zA-Z0-9]", "", raw))
        fields.append({
            "name": fname, "cap": cap(fname), "col": camel_to_snake(fname),
            "java": java_type, "sql": sql_type, "fq": fq,
            "searchable": searchable, "anns": anns,
        })

    module_pkg = f"{base}.{module}"
    dto_pkg = f"{module_pkg}.dto"
    mapper_pkg = f"{module_pkg}.mapper"
    common_pkg = f"{base}.common"
    exc_pkg = f"{base}.common.exception"

    module_dir = JAVA_MAIN.joinpath(*module_pkg.split("."))
    if module_dir.exists() and any(module_dir.iterdir()) and not args.force:
        fail(f"module package '{module_pkg}' already exists (use --force)")

    field_fq = [f["fq"] for f in fields if f["fq"]]
    searchable = [f for f in fields if f["searchable"]]

    base_ctx = {
        "base": base, "module_pkg": module_pkg, "dto_pkg": dto_pkg,
        "mapper_pkg": mapper_pkg, "common_pkg": common_pkg,
        "Entity": Entity, "entity": entity, "alias": alias,
        "plural": plural, "PluralCap": plural_cap,
        "idField": id_field, "IdFieldCap": cap(id_field),
        "createdField": created_field, "CreatedCap": cap(created_field),
        "updatedField": updated_field, "UpdatedCap": cap(updated_field),
    }

    # ---- Entity ----
    entity_imports = ["jakarta.persistence.*", "lombok.Getter", "lombok.Setter",
                      "org.hibernate.annotations.UuidGenerator",
                      "org.hibernate.id.uuid.UuidVersion7Strategy",
                      "java.time.LocalDateTime", "java.util.UUID"] + field_fq
    decls = "".join(f"    private {f['java']} {f['name']};\n\n" for f in fields)
    soft_decls = ""
    if args.soft_delete:
        soft_decls = (f"\n    private Boolean {deleted_field};\n\n"
                      f"    private LocalDateTime {deleted_at_field};\n")
    entity_java = render(ENTITY_T, {**base_ctx,
                                    "imports": imports_block(entity_imports),
                                    "field_decls": decls, "soft_decls": soft_decls})

    # ---- SaveRequest ----
    save_imports = list(LOMBOK_DTO) + ["java.util.UUID"] + field_fq
    for f in fields:
        for a in f["anns"]:
            save_imports.append(VALIDATION_IMPORTS[a])
    save_fields = []
    for f in fields:
        for a in f["anns"]:
            save_fields.append(f"    {a}")
        save_fields.append(f"    private {f['java']} {f['name']};\n")
    save_java = render(SAVE_REQUEST_T, {**base_ctx,
                                        "imports": imports_block(save_imports),
                                        "fields": "\n".join(save_fields)})

    # ---- ListRequest ----
    list_java = render(LIST_REQUEST_T, base_ctx)

    # ---- Response ----
    resp_imports = list(LOMBOK_DTO) + ["java.time.LocalDateTime", "java.util.UUID"] + field_fq
    resp_fields = "".join(f"    private {f['java']} {f['name']};\n\n" for f in fields)
    resp_java = render(RESPONSE_T, {**base_ctx,
                                    "imports": imports_block(resp_imports),
                                    "fields": resp_fields})

    # ---- Mapper ----
    mapper_imports = [f"{module_pkg}.{Entity}", f"{dto_pkg}.Save{Entity}Request",
                      f"{dto_pkg}.{Entity}Response", "org.springframework.stereotype.Component"]
    set_lines = "\n".join(f"        entity.set{f['cap']}(request.get{f['cap']}());" for f in fields)
    builder_lines = "\n".join(f"                .{f['name']}(entity.get{f['cap']}())" for f in fields)
    mapper_java = render(MAPPER_T, {**base_ctx,
                                    "imports": imports_block(mapper_imports),
                                    "set_lines": set_lines, "builder_lines": builder_lines})

    # ---- Repository ----
    repo_imports = ["org.springframework.data.domain.Page",
                    "org.springframework.data.domain.Pageable",
                    "org.springframework.data.jpa.repository.JpaRepository",
                    "java.util.UUID"]
    search_method = ""
    if searchable:
        repo_imports += ["org.springframework.data.jpa.repository.Query",
                         "org.springframework.data.repository.query.Param"]
        conditions = "\n               OR ".join(
            f"LOWER({alias}.{f['name']}) LIKE CONCAT('%', :search, '%')" for f in searchable
        )
        search_method = render(SEARCH_METHOD_T, {**base_ctx, "conditions": conditions})
    repo_java = render(REPOSITORY_T, {**base_ctx,
                                      "imports": imports_block(repo_imports),
                                      "search_method": search_method})

    # ---- Service ----
    service_imports = [
        f"{exc_pkg}.NotFoundException",
        f"{dto_pkg}.Save{Entity}Request", f"{dto_pkg}.{Entity}ListRequest",
        f"{dto_pkg}.{Entity}Response", f"{mapper_pkg}.{Entity}Mapper",
        "lombok.RequiredArgsConstructor",
        "org.springframework.data.domain.Page", "org.springframework.data.domain.PageRequest",
        "org.springframework.data.domain.Pageable", "org.springframework.data.domain.Sort",
        "org.springframework.stereotype.Service",
        "org.springframework.transaction.annotation.Transactional",
        "java.time.LocalDateTime", "java.util.UUID",
    ]
    if searchable:
        page_block = (f"        Page<{Entity}> page;\n"
                      f"        if (!search.isBlank()) {{\n"
                      f"            page = {entity}Repository.search{plural_cap}(search.toLowerCase(), pageable);\n"
                      f"        }} else {{\n"
                      f"            page = {entity}Repository.findAll(pageable);\n"
                      f"        }}")
    else:
        page_block = f"        Page<{Entity}> page = {entity}Repository.findAll(pageable);"
    if args.soft_delete:
        delete_block = (
            f"        {Entity} entity = {entity}Repository.findById(id)\n"
            f"                .orElseThrow(() -> new NotFoundException(\"{Entity} not found with id: \" + id));\n"
            f"        entity.set{cap(deleted_field)}(true);\n"
            f"        entity.set{cap(deleted_at_field)}(LocalDateTime.now());\n"
            f"        {entity}Repository.save(entity);"
        )
    else:
        delete_block = (
            f"        if (!{entity}Repository.existsById(id)) {{\n"
            f"            throw new NotFoundException(\"{Entity} not found with id: \" + id);\n"
            f"        }}\n"
            f"        {entity}Repository.deleteById(id);"
        )
    service_java = render(SERVICE_T, {**base_ctx,
                                      "imports": imports_block(service_imports),
                                      "page_block": page_block, "delete_block": delete_block})

    # ---- Controller ----
    controller_imports = [
        f"{common_pkg}.ApiResponse",
        f"{dto_pkg}.Save{Entity}Request", f"{dto_pkg}.{Entity}ListRequest",
        f"{dto_pkg}.{Entity}Response",
        "jakarta.validation.Valid", "lombok.RequiredArgsConstructor",
        "org.springframework.data.domain.Page",
        "org.springframework.http.ResponseEntity",
        "org.springframework.web.bind.annotation.*",
        "java.util.List", "java.util.UUID",
    ]
    controller_java = render(CONTROLLER_T, {**base_ctx,
                                            "imports": imports_block(controller_imports)})

    # ---- Migration ----
    version = next_migration_version()
    col_lines = "".join(f"    {f['col']} {f['sql']},\n" for f in fields)
    soft_cols = ""
    if args.soft_delete:
        soft_cols = (f"    {camel_to_snake(deleted_field)} boolean,\n"
                     f"    {camel_to_snake(deleted_at_field)} timestamp(6),\n")
    migration_sql = render(MIGRATION_T, {
        "Entity": Entity, "plural": plural,
        "idCol": camel_to_snake(id_field),
        "createdCol": camel_to_snake(created_field),
        "updatedCol": camel_to_snake(updated_field),
        "col_lines": col_lines, "soft_cols": soft_cols,
    })

    # ---- Write everything ----
    print(f"Generating module '{module}' (Entity {Entity}, prefix '{prefix}') in {base}")
    write(module_dir / f"{Entity}.java", entity_java, args.force)
    write(module_dir / f"{Entity}Repository.java", repo_java, args.force)
    write(module_dir / f"{Entity}Service.java", service_java, args.force)
    write(module_dir / f"{Entity}Controller.java", controller_java, args.force)
    write(module_dir / "dto" / f"Save{Entity}Request.java", save_java, args.force)
    write(module_dir / "dto" / f"{Entity}ListRequest.java", list_java, args.force)
    write(module_dir / "dto" / f"{Entity}Response.java", resp_java, args.force)
    write(module_dir / "mapper" / f"{Entity}Mapper.java", mapper_java, args.force)
    write(MIGRATIONS / f"V{version}__create_{plural}_table.sql", migration_sql, args.force)

    print(f"\nDone. Endpoints: POST /api/{plural}/save, POST /api/{plural}/list, DELETE /api/{plural}/{{id}}")


def next_migration_version():
    max_v = 0
    if MIGRATIONS.is_dir():
        for f in MIGRATIONS.glob("V*__*.sql"):
            m = re.match(r"V(\d+)__", f.name)
            if m:
                max_v = max(max_v, int(m.group(1)))
    return max_v + 1


if __name__ == "__main__":
    main()
