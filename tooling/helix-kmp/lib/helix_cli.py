#!/usr/bin/env python3
"""Thin, deterministic P1 commands for the repository-local Helix KMP CLI."""

from __future__ import annotations

import argparse
from collections import deque
import difflib
import fnmatch
from functools import lru_cache
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Iterable


RULE_TEXT = {
    "DEP-FEATURE-FEATURE-PUBLIC-PRESENTATION-ONLY": "A Feature may consume only another Feature's public presentation API.",
    "DEP-ROLE-DENIED": "The default-deny dependency policy permits only the target roles listed for the source role.",
    "EXC-EXPIRED": "Architecture exceptions stop applying after their registered expiry date.",
    "FEATURE-PUBLIC-SURFACE-OUTSIDE-API": "A Feature's public declarations must live below an api directory.",
    "GRAPH-CYCLE-LOGICAL": "API and implementation families must remain acyclic after family collapse.",
    "GRAPH-CYCLE-PHYSICAL": "Main-source project dependencies must form a directed acyclic graph.",
    "MOD-PATH-ROLE-MISMATCH": "A module path must match the role convention plugin it applies.",
    "MOD-ROLE-MISSING": "Every runtime module must apply exactly one Helix role convention plugin.",
    "MOD-ROLE-MULTIPLE": "A module may own exactly one Helix role.",
    "POLICY-DRIFT": "The checked-in dependency policy must match the normative policy block.",
}
RULE_IDS = tuple(RULE_TEXT)

ROLE_METADATA = {
    "app": ("app/*", "Composition roots and platform entry points"),
    "feature": ("feature/*", "Screens, Cells, ViewModels, Outputs"),
    "ui": ("ui/*", "Stateless rendering only"),
    "capability_api": ("capability/*-api", "Queries, Commands, product models"),
    "capability_impl": ("capability/*-impl", "Internal implementation; public Koin module only"),
    "foundation_api": ("foundation/*", "Cross-cutting contracts"),
    "foundation_runtime": ("foundation/*", "Cross-cutting runtime"),
    "platform": ("platform/*", "Platform and OS seams"),
    "platform_api": ("platform/*-api", "Platform seam contracts"),
    "platform_impl": ("platform/*-impl", "Platform seam implementations"),
    "storage": ("storage/*", "Shared product databases"),
    "testkit": ("testkit/*", "Test-only fakes and fixtures"),
}

SECTION_NAMES = ("stage", "module-roles", "workflow", "policy-prohibitions", "future-commands")
PUBLIC_DECLARATION = re.compile(
    r"^(?:public[ \t]+)?(?:data[ \t]+class|enum[ \t]+class|sealed[ \t]+(?:class|interface)|class|interface|object|fun|val|var|typealias)[ \t]+([A-Za-z_]\w*)",
    re.MULTILINE,
)


class CliError(RuntimeError):
    pass


def dump_json(value: Any) -> None:
    print(json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False))


def kotlin_files(root: Path, source_only: bool = False) -> list[Path]:
    if not root.exists():
        return []
    files = []
    for path in root.rglob("*.kt"):
        relative = path.as_posix()
        if "/build/" in relative or "/generated/" in relative:
            continue
        if source_only and not re.search(r"/src/[^/]*Main/", relative):
            continue
        files.append(path)
    return sorted(files)


def path_size(paths: Iterable[Path]) -> dict[str, int]:
    byte_count = 0
    line_count = 0
    for path in paths:
        data = path.read_bytes()
        byte_count += len(data)
        line_count += data.count(b"\n") + (1 if data and not data.endswith(b"\n") else 0)
    return {"bytes": byte_count, "lines": line_count, "estimatedTokens": (byte_count + 3) // 4}


def line_at(text: str, pos: int) -> str:
    end = text.find("\n", pos)
    return text[pos : len(text) if end == -1 else end]


class Repository:
    def __init__(self, root: Path) -> None:
        self.root = root.resolve()
        self.report_path = self.root / "build/reports/helix/module-graph.json"

    def refresh(self, no_refresh: bool) -> None:
        if no_refresh:
            return
        result = subprocess.run(
            [str(self.root / "gradlew"), "checkModuleGraph", "-q"],
            cwd=self.root,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode and not self.report_path.exists():
            detail = (result.stderr or result.stdout).strip()
            raise CliError(f"checkModuleGraph failed before producing a report: {detail}")

    def graph(self, no_refresh: bool) -> dict[str, Any]:
        self.refresh(no_refresh)
        if not self.report_path.exists():
            raise CliError("module graph report is missing; run ./gradlew checkModuleGraph")
        graph = json.loads(self.report_path.read_text(encoding="utf-8"))
        if graph.get("schema") != 2:
            raise CliError("module graph schema 2 is required; refresh the report")
        graph["nodes"] = sorted(graph.get("nodes", []), key=lambda item: item["path"])
        graph["edges"] = sorted(
            graph.get("edges", []),
            key=lambda item: (item["from"], item["to"], item.get("configuration", "")),
        )
        graph["findings"] = sorted(
            graph.get("findings", []), key=lambda item: (item.get("rule", ""), item.get("subject", ""))
        )
        return graph

    def nodes(self, graph: dict[str, Any]) -> dict[str, dict[str, Any]]:
        return {node["path"]: node for node in graph["nodes"]}

    def resolve(self, target: str, graph: dict[str, Any]) -> list[dict[str, Any]]:
        nodes = self.nodes(graph)
        if target in nodes:
            return [nodes[target]]
        candidate = Path(target)
        if candidate.is_absolute():
            try:
                candidate = candidate.resolve().relative_to(self.root)
            except ValueError:
                candidate = Path(target)
        candidate_text = candidate.as_posix().lstrip("./")
        file_matches = [
            node
            for node in graph["nodes"]
            if candidate_text == node["projectDir"]
            or candidate_text.startswith(node["projectDir"].rstrip("/") + "/")
        ]
        if file_matches:
            return sorted(file_matches, key=lambda node: len(node["projectDir"]), reverse=True)[:1]

        simple_name = target.rsplit(".", 1)[-1]
        symbol_matches = []
        word = re.compile(rf"\b{re.escape(simple_name)}\b")
        for node in graph["nodes"]:
            module_root = self.root / node["projectDir"]
            matched = False
            for path in kotlin_files(module_root, source_only=True):
                text = path.read_text(encoding="utf-8")
                if not word.search(text):
                    continue
                if "." in target:
                    package = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
                    if package and f"{package.group(1)}.{simple_name}" != target:
                        continue
                if re.search(rf"\b(?:class|interface|object|typealias|fun|val|var)\s+{re.escape(simple_name)}\b", text):
                    matched = True
                    break
            if matched:
                symbol_matches.append(node)
        if symbol_matches:
            return sorted(symbol_matches, key=lambda node: node["path"])
        raise CliError(f"cannot resolve target '{target}' to a module, file, or Kotlin declaration")

    def files_in_dirs(self, directories: Iterable[str]) -> list[Path]:
        result: list[Path] = []
        for directory in directories:
            result.extend(kotlin_files(self.root / directory))
        return sorted(set(result))


def adjacency(graph: dict[str, Any], src: str, dst: str) -> dict[str, list[str]]:
    result = {node["path"]: [] for node in graph["nodes"]}
    for edge in graph["edges"]:
        result.setdefault(edge[src], []).append(edge[dst])
    return {key: sorted(set(value)) for key, value in sorted(result.items())}


def reverse_map(graph: dict[str, Any]) -> dict[str, list[str]]:
    return adjacency(graph, "to", "from")


def forward_map(graph: dict[str, Any]) -> dict[str, list[str]]:
    return adjacency(graph, "from", "to")


def closure(starts: Iterable[str], adjacency: dict[str, list[str]]) -> list[str]:
    seen = set(starts)
    queue = deque(sorted(starts))
    while queue:
        item = queue.popleft()
        for next_item in adjacency.get(item, []):
            if next_item not in seen:
                seen.add(next_item)
                queue.append(next_item)
    return sorted(seen - set(starts))


def finding_line(finding: dict[str, Any]) -> str:
    return f"[{finding['rule']}] {finding['subject']} -- {finding['problem']}. Fix: {finding['fix']}"


def command_graph(repo: Repository, args: argparse.Namespace) -> int:
    graph = repo.graph(args.no_refresh)
    reverse_edges = [
        {
            "from": edge["to"],
            "to": edge["from"],
            "configuration": edge.get("configuration", "other"),
        }
        for edge in graph["edges"]
    ]
    reverse_edges.sort(key=lambda edge: (edge["from"], edge["to"], edge["configuration"]))
    output = dict(graph)
    output["reverseEdges"] = reverse_edges
    if args.module:
        target = repo.resolve(args.module, graph)[0]["path"]
        neighbours = {target}
        for edge in graph["edges"]:
            if edge["from"] == target:
                neighbours.add(edge["to"])
            if edge["to"] == target:
                neighbours.add(edge["from"])
        output["nodes"] = [node for node in graph["nodes"] if node["path"] in neighbours]
        output["edges"] = [
            edge for edge in graph["edges"] if edge["from"] in neighbours and edge["to"] in neighbours
        ]
        output["reverseEdges"] = [
            edge for edge in reverse_edges if edge["from"] in neighbours and edge["to"] in neighbours
        ]
        output["findings"] = [
            finding
            for finding in graph["findings"]
            if any(module in finding.get("subject", "") for module in neighbours)
        ]
    if args.json:
        dump_json(output)
        return 0
    forwards = forward_map(output)
    reverses = reverse_map(output)
    for node in output["nodes"]:
        print(f"{node['path']} [{node.get('role') or 'unassigned'}]")
        print(f"  targets: {', '.join(node.get('targets', [])) or 'none'}")
        print(f"  public API: {', '.join(node.get('publicApiDirs', [])) or 'none'}")
        print(f"  depends on: {', '.join(forwards.get(node['path'], [])) or 'none'}")
        print(f"  depended on by: {', '.join(reverses.get(node['path'], [])) or 'none'}")
    cycles = [finding_line(finding) for finding in graph["findings"] if finding["rule"].startswith("GRAPH-CYCLE")]
    print("Cycles:")
    print("\n".join(f"  {cycle}" for cycle in cycles) if cycles else "  none")
    return 0


@lru_cache(maxsize=None)
def codeowner_rules(root: Path) -> tuple[tuple[str, tuple[str, ...]], ...]:
    path = root / ".github/CODEOWNERS"
    if not path.exists():
        return ()
    rules = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        rules.append((parts[0].lstrip("/"), tuple(parts[1:])))
    return tuple(rules)


def codeowners(repo: Repository, module: dict[str, Any]) -> list[str]:
    owners: tuple[str, ...] = ()
    module_dir = module["projectDir"] + "/"
    for pattern, candidates in codeowner_rules(repo.root):
        if fnmatch.fnmatch(module_dir, pattern) or fnmatch.fnmatch(module_dir.rstrip("/"), pattern):
            owners = candidates
    return sorted(set(owners)) or ["unassigned"]


def public_packages(repo: Repository, module: dict[str, Any]) -> list[str]:
    packages = []
    for path in repo.files_in_dirs(module.get("publicApiDirs", [])):
        match = re.search(r"^package\s+([\w.]+)", path.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            packages.append(match.group(1))
    return sorted(set(packages))


def public_consumers(repo: Repository, graph: dict[str, Any], target: dict[str, Any]) -> list[str]:
    packages = public_packages(repo, target)
    if not packages:
        return []
    consumers = []
    for node in graph["nodes"]:
        if node["path"] == target["path"]:
            continue
        for path in kotlin_files(repo.root / node["projectDir"], source_only=True):
            imports = re.findall(r"^import\s+([\w.]+)", path.read_text(encoding="utf-8"), re.MULTILINE)
            if any(any(item == package or item.startswith(package + ".") for package in packages) for item in imports):
                consumers.append(node["path"])
                break
    return sorted(set(consumers))


def tests_for(node: dict[str, Any]) -> str | None:
    targets = node.get("targets", [])
    if "jvm" in targets:
        return f"{node['path']}:jvmTest"
    if targets == ["wasmJs"]:
        return None
    return f"{node['path']}:test"


def command_impact(repo: Repository, args: argparse.Namespace) -> int:
    graph = repo.graph(args.no_refresh)
    targets = repo.resolve(args.target, graph)
    nodes = repo.nodes(graph)
    reverses = reverse_map(graph)
    direct = sorted(set(value for target in targets for value in reverses.get(target["path"], [])))
    transitive = closure([target["path"] for target in targets], reverses)
    affected_paths = sorted(set([target["path"] for target in targets] + transitive))
    affected = [nodes[path] for path in affected_paths]
    policy = json.loads((repo.root / "config/helix/dependency-policy.json").read_text(encoding="utf-8"))
    surprising = []
    target_paths = {target["path"] for target in targets}
    for edge in graph["edges"]:
        if edge["to"] not in target_paths and edge["to"] not in transitive:
            continue
        source = nodes[edge["from"]]
        destination = nodes[edge["to"]]
        allowed = policy.get("roles", {}).get(source.get("role"), {}).get("allow", [])
        same_role_ok = (
            source.get("role") == destination.get("role")
            and source.get("role") in ("app", "feature")
        )
        if source.get("role") == "testkit":
            continue
        if destination.get("role") not in allowed and not same_role_ok:
            surprising.append(f"{edge['from']} -> {edge['to']} (forbidden by role policy)")
        elif source.get("role") == "feature" and destination.get("role") == "capability_impl":
            surprising.append(f"{edge['from']} -> {edge['to']} (Feature depends on Capability impl)")
    consumers = sorted(set(value for target in targets for value in public_consumers(repo, graph, target)))
    result = {
        "target": args.target,
        "resolvedModules": [target["path"] for target in targets],
        "directReverseDependents": direct,
        "transitiveReverseDependents": transitive,
        "tests": sorted(set([task for node in affected if (task := tests_for(node))] + ["checkModuleGraph"])),
        "owners": {node["path"]: codeowners(repo, node) for node in affected},
        "publicConsumers": consumers,
        "qualificationSuites": ["verifyFast"] + (["verifyFull"] if any(node.get("role") == "app" for node in affected) else []),
        "expected": sorted(set(direct + consumers)),
        "surprising": sorted(set(surprising)),
    }
    if args.json:
        dump_json(result)
    else:
        print(f"Target: {args.target} -> {', '.join(result['resolvedModules'])}")
        for label, key in (
            ("Direct reverse dependents", "directReverseDependents"),
            ("Transitive reverse dependents", "transitiveReverseDependents"),
            ("Tests", "tests"),
            ("Public consumers", "publicConsumers"),
            ("Qualification suites", "qualificationSuites"),
            ("Expected impact", "expected"),
            ("Surprising impact", "surprising"),
        ):
            values = result[key]
            print(f"{label}: {', '.join(values) if values else 'none'}")
        print("Owners:")
        for module, owners in result["owners"].items():
            print(f"  {module}: {', '.join(owners)}")
    return 0


def public_api_size(repo: Repository, node: dict[str, Any]) -> int:
    return len(declaration_lines(repo.files_in_dirs(node.get("publicApiDirs", []))))


def git_commits(repo: Repository) -> tuple[list[list[str]] | None, str | None]:
    try:
        result = subprocess.run(
            ["git", "log", "-n", "200", "--format=__HELIX_COMMIT__%x20%H", "--name-only"],
            cwd=repo.root,
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError as error:
        reason = str(error).splitlines()[0] if str(error).splitlines() else error.__class__.__name__
        return None, f"git history unavailable: {reason}"
    if result.returncode:
        detail = (result.stderr or result.stdout).strip().splitlines()
        reason = detail[0] if detail else f"git exited {result.returncode}"
        return None, f"git history unavailable: {reason}"
    commits = []
    for chunk in result.stdout.split("__HELIX_COMMIT__"):
        lines = [line.strip() for line in chunk.splitlines() if line.strip()]
        if lines:
            commits.append(lines[1:])
    return commits, None


def git_cochange(
    node: dict[str, Any],
    features: list[str],
    commits: list[list[str]] | None,
    unavailable_note: str | None,
) -> dict[str, Any]:
    if commits is None:
        return {"status": "skipped", "note": unavailable_note or "git history unavailable"}
    module_prefix = node["projectDir"].rstrip("/") + "/"
    module_commits = 0
    shared = 0
    for files in commits:
        touches_module = any(path.startswith(module_prefix) for path in files)
        if not touches_module:
            continue
        module_commits += 1
        if any(any(path.startswith(feature.rstrip("/") + "/") for feature in features) for path in files):
            shared += 1
    share = round(shared / module_commits, 4) if module_commits else 0.0
    return {"status": "available", "moduleCommits": module_commits, "sharedWithFeatureCommits": shared, "share": share}


def command_doctor(repo: Repository, args: argparse.Namespace) -> int:
    graph = repo.graph(args.no_refresh)
    nodes_by_path = repo.nodes(graph)
    selected = graph["nodes"]
    if args.scope:
        selected = repo.resolve(args.scope, graph)
    selected_paths = {node["path"] for node in selected}
    findings = [
        finding
        for finding in graph["findings"]
        if not args.scope or any(path in finding.get("subject", "") for path in selected_paths)
    ]
    exceptions_data = json.loads((repo.root / "config/helix/exceptions.json").read_text(encoding="utf-8"))
    reverses = reverse_map(graph)
    features = [node["projectDir"] for node in graph["nodes"] if node.get("role") == "feature"]
    commits, git_note = git_commits(repo)
    pressure = []
    recommendations = []
    for node in selected:
        files = kotlin_files(repo.root / node["projectDir"])
        relevant_exceptions = [
            item for item in exceptions_data.get("exceptions", []) if node["path"] in item.get("scope", "")
        ]
        item = {
            "module": node["path"],
            "incomingDependencyCount": len(reverses.get(node["path"], [])),
            "publicApiDeclarations": public_api_size(repo, node),
            "contextSurface": path_size(files),
            "liveExceptions": sorted(relevant_exceptions, key=lambda entry: (entry.get("expires", ""), entry.get("rule", ""))),
            "gitCoChange": git_cochange(node, features, commits, git_note),
        }
        pressure.append(item)
        if node.get("role") == "capability_impl" and node["path"].endswith("-impl"):
            api_path = node["path"][:-5] + "-api"
            feature_users = sorted(
                dependent
                for dependent in reverses.get(api_path, [])
                if nodes_by_path.get(dependent, {}).get("role") == "feature"
            )
            if len(feature_users) > 1:
                recommendations.append(
                    {
                        "module": node["path"],
                        "recommendation": "keep split",
                        "confidence": "HIGH",
                        "evidence": f"{len(feature_users)} Feature consumers use {api_path}: {', '.join(feature_users)}",
                    }
                )
        peer_consumers = sorted(
            dependent
            for dependent in reverses.get(node["path"], [])
            if node.get("role") == "feature" and nodes_by_path.get(dependent, {}).get("role") == "feature"
        )
        if peer_consumers:
            recommendations.append(
                {
                    "module": node["path"],
                    "recommendation": "extract shared UI or Capability",
                    "confidence": "HIGH",
                    "evidence": f"Feature consumers: {', '.join(peer_consumers)}",
                }
            )
    diagnoses = []
    for finding in findings:
        diagnoses.append(
            {
                "finding": finding_line(finding),
                "violation": finding["subject"],
                "rule": finding["rule"],
                "ruleText": RULE_TEXT.get(finding["rule"], "Stable Helix architecture rule."),
                "approvedRepair": finding["fix"],
                "mechanicalRecipeAvailable": False,
            }
        )
    result = {
        "scope": args.scope or "repository",
        "findings": diagnoses,
        "pressureSignals": pressure,
        "recommendations": sorted(recommendations, key=lambda item: (item["module"], item["recommendation"])),
        "editsFiles": False,
    }
    if args.json:
        dump_json(result)
    else:
        if not diagnoses:
            print("Findings: none")
        for diagnosis in diagnoses:
            print(diagnosis["finding"])
            if args.explain:
                print(f"Violation: {diagnosis['violation']}")
                print(f"Rule: {diagnosis['rule']} — {diagnosis['ruleText']}")
                print(f"Approved repair: {diagnosis['approvedRepair']}")
                print("Mechanical recipe available: no")
        print("Architecture pressure:")
        for item in pressure:
            surface = item["contextSurface"]
            print(
                f"  {item['module']}: incoming={item['incomingDependencyCount']}, "
                f"public API={item['publicApiDeclarations']}, context={surface['bytes']} bytes/"
                f"{surface['lines']} lines/~{surface['estimatedTokens']} estimated tokens"
            )
            cochange = item["gitCoChange"]
            if cochange["status"] == "available":
                print(f"    git co-change with a Feature: {cochange['share']:.1%} ({cochange['sharedWithFeatureCommits']}/{cochange['moduleCommits']})")
            else:
                print(f"    git co-change: skipped ({cochange['note']})")
            if item["liveExceptions"]:
                for exception in item["liveExceptions"]:
                    print(f"    exception {exception['rule']} expires {exception['expires']}")
        print("Recommendations:")
        if recommendations:
            for recommendation in recommendations:
                print(f"  {recommendation['module']}: {recommendation['recommendation']} — {recommendation['confidence']}")
                print(f"    evidence: {recommendation['evidence']}")
        else:
            print("  none")
    return 1 if findings else 0


def declaration_lines(paths: Iterable[Path]) -> list[str]:
    values = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for match in PUBLIC_DECLARATION.finditer(text):
            line = line_at(text, match.start()).strip()
            if line and not line.startswith(("internal ", "private ", "protected ")):
                values.append(f"{path.name}: {line}")
    return sorted(set(values))


def file_payload(repo: Repository, paths: Iterable[Path], files_only: bool) -> list[dict[str, Any]]:
    values = []
    for path in sorted(set(paths)):
        relative = path.relative_to(repo.root).as_posix()
        data = path.read_text(encoding="utf-8")
        size = path_size([path])
        item: dict[str, Any] = {
            "path": relative,
            "bytes": size["bytes"],
            "lines": size["lines"],
        }
        if not files_only:
            item["content"] = data
        values.append(item)
    return values


def protected_notes(repo: Repository, node: dict[str, Any]) -> list[str]:
    paths = kotlin_files(repo.root / node["projectDir"])
    text = "\n".join(path.read_text(encoding="utf-8") for path in paths).lower()
    notes = []
    if any(word in text or word in node["projectDir"].lower() for word in ("auth", "session", "credential")):
        notes.append("Protected area: auth/session requires explicit experienced review.")
    if node.get("role") == "storage" or "database" in text:
        notes.append("Protected area: storage and irreversible migrations require explicit experienced review when applicable.")
    if node.get("role") == "capability_api":
        notes.append("Protected area: public Capability API changes require explicit experienced review.")
    return notes


def module_tasks(node: dict[str, Any]) -> list[str]:
    return [f"{node['path']}:verifyFastModule"]


def command_context(repo: Repository, args: argparse.Namespace) -> int:
    graph = repo.graph(args.no_refresh)
    node = repo.resolve(args.target, graph)[0]
    nodes = repo.nodes(graph)
    forwards = forward_map(graph)
    reverses = reverse_map(graph)
    policy = json.loads((repo.root / "config/helix/dependency-policy.json").read_text(encoding="utf-8"))
    target_files = kotlin_files(repo.root / node["projectDir"])
    public_files = repo.files_in_dirs(node.get("publicApiDirs", []))
    dependency_items = []
    for dependency_path in forwards.get(node["path"], []):
        dependency = nodes[dependency_path]
        if dependency.get("role") in ("capability_api", "foundation_api", "ui"):
            files = kotlin_files(repo.root / dependency["projectDir"], source_only=True)
        elif dependency.get("publicApiDirs"):
            files = repo.files_in_dirs(dependency["publicApiDirs"])
        else:
            files = []
        dependency_items.append(
            {
                "module": dependency_path,
                "files": file_payload(repo, files, args.files_only),
                "publicDeclarations": [] if files else declaration_lines(kotlin_files(repo.root / dependency["projectDir"], source_only=True)),
            }
        )
    local_fixture_files = [path for path in target_files if path.name.endswith("Fixtures.kt")]
    fixture_files = sorted(
        local_fixture_files
        + [
            path
            for testkit in graph["nodes"]
            if testkit.get("role") == "testkit"
            for path in kotlin_files(repo.root / testkit["projectDir"])
            if path.name.endswith("Fixtures.kt")
        ]
    )
    adr_source = (repo.root / "docs/architecture/adr/0001-helix-adoption.md").read_text(encoding="utf-8")
    adr_match = re.search(r"^## Revisit when\s*$\n(.*?)(?=^## |\Z)", adr_source, re.MULTILINE | re.DOTALL)
    adr_lines = [line.strip() for line in adr_match.group(1).splitlines() if line.strip()] if adr_match else []
    role = node.get("role")
    role_rules = [
        "EXC-EXPIRED",
        "MOD-ROLE-MISSING",
        "MOD-ROLE-MULTIPLE",
        "MOD-PATH-ROLE-MISMATCH",
        "GRAPH-CYCLE-PHYSICAL",
        "GRAPH-CYCLE-LOGICAL",
    ]
    if role == "feature":
        role_rules += ["DEP-ROLE-DENIED", "DEP-FEATURE-FEATURE-PUBLIC-PRESENTATION-ONLY", "FEATURE-PUBLIC-SURFACE-OUTSIDE-API"]
    elif role != "testkit":
        role_rules += ["DEP-ROLE-DENIED"]
    sections: dict[str, Any] = {
        "TASK TARGET": {"requested": args.target, "module": node["path"], "role": role, "owner": codeowners(repo, node), "targets": node.get("targets", [])},
        "PUBLIC ENTRY POINTS": declaration_lines(public_files),
        "GRAPH SLICE": {"dependencies": forwards.get(node["path"], []), "reverseDependencies": reverses.get(node["path"], [])},
        "RULES": {
            "allowedDependencyRoles": policy.get("roles", {}).get(role, {}).get("allow", []),
            "ruleIds": sorted(set(role_rules)),
            "protectedAreas": protected_notes(repo, node),
            "adrRevisitWhen": adr_lines,
        },
        "SOURCE": file_payload(repo, target_files, args.files_only),
        "DEPENDENCY CONTEXT": dependency_items,
        "ACCEPTANCE": {
            "fixtures": file_payload(repo, fixture_files, True),
            "publicValuesAndFunctions": [
                f"{item['file']}:{item['signature']}"
                for item in fixture_declarations(fixture_files, include_internal=local_fixture_files)
            ],
        },
        "VERIFY": {
            "fastAffected": "tooling/helix-kmp/helix-kmp verify --fast --affected",
            "moduleTasks": module_tasks(node),
        },
    }
    serialized = json.dumps(sections, indent=2, sort_keys=True, ensure_ascii=False).encode("utf-8")
    totals = {"bytes": len(serialized), "lines": serialized.count(b"\n") + 1, "estimatedTokens": (len(serialized) + 3) // 4}
    result = {"sections": sections, "packetSize": totals, "tokenCountIsEstimate": True}
    if args.json:
        dump_json(result)
        return 0
    def print_items(label: str, values: Iterable[Any], indent: str = "  ") -> None:
        print(f"{indent}{label}:")
        rendered = list(values)
        if rendered:
            for value in rendered:
                print(f"{indent}  {value}")
        else:
            print(f"{indent}  none")

    def print_files(files: Iterable[dict[str, Any]], indent: str = "  ") -> None:
        rendered = list(files)
        if not rendered:
            print(f"{indent}none")
            return
        for item in rendered:
            print(f"{indent}{item['path']} ({item['bytes']} bytes, {item['lines']} lines)")
            if "content" in item:
                print(item["content"], end="" if item["content"].endswith("\n") else "\n")

    for heading, content in sections.items():
        print(heading)
        if heading == "TASK TARGET":
            for key in ("requested", "module", "role", "owner", "targets"):
                value = content[key]
                rendered = ", ".join(value) if isinstance(value, list) else value
                print(f"  {key}: {rendered or 'none'}")
        elif heading == "PUBLIC ENTRY POINTS":
            for declaration in content or ["none"]:
                print(f"  {declaration}")
        elif heading == "GRAPH SLICE":
            print_items("dependencies", content["dependencies"])
            print_items("reverse dependencies", content["reverseDependencies"])
        elif heading == "RULES":
            print_items("allowed dependency roles", content["allowedDependencyRoles"])
            print_items("rule ids", content["ruleIds"])
            print_items("protected areas", content["protectedAreas"])
            print_items("ADR revisit when", content["adrRevisitWhen"])
        elif heading == "SOURCE":
            print_files(content)
        elif heading == "DEPENDENCY CONTEXT":
            if not content:
                print("  none")
            for dependency in content:
                print(f"  {dependency['module']}")
                print_files(dependency["files"], "    ")
                if dependency["publicDeclarations"]:
                    print_items("public declarations", dependency["publicDeclarations"], "    ")
        elif heading == "ACCEPTANCE":
            print_items("fixtures", (item["path"] for item in content["fixtures"]))
            print_items("public values and functions", content["publicValuesAndFunctions"])
        elif heading == "VERIFY":
            print(f"  fastAffected: {content['fastAffected']}")
            print(f"  moduleTasks: {', '.join(content['moduleTasks']) or 'none'}")
    print(
        f"PACKET SIZE: {totals['bytes']} bytes, {totals['lines']} lines, "
        f"~{totals['estimatedTokens']} estimated tokens (4 chars/token estimate)"
    )
    return 0


def composables(paths: Iterable[Path]) -> list[dict[str, Any]]:
    values = []
    pattern = re.compile(r"@Composable\s+(?:public\s+)?fun\s+([A-Za-z_]\w*)\s*\((.*?)\)\s*(?::[^={]+)?[={]", re.DOTALL)
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for match in pattern.finditer(text):
            prefix = text[max(0, match.start() - 20) : match.start()]
            signature = match.group(0)
            if "internal " in prefix or "private " in prefix:
                continue
            values.append(
                {
                    "name": match.group(1),
                    "parameters": " ".join(match.group(2).split()),
                    "cell": match.group(1).endswith("Cell") and bool(re.search(r"instanceKey\s*:\s*FeatureInstanceKey", match.group(2))),
                    "file": path,
                    "signature": signature,
                }
            )
    return sorted(values, key=lambda item: item["name"])


def fixture_declarations(
    paths: Iterable[Path], include_internal: Iterable[Path] = ()
) -> list[dict[str, str]]:
    values = []
    included_internal_paths = {path.resolve() for path in include_internal}
    owner_pattern = re.compile(
        r"^[ \t]*(?:(public|internal|private)[ \t]+)?object[ \t]+\w*Fixtures\b[^\n{]*\{",
        re.MULTILINE,
    )
    member_pattern = re.compile(
        r"^[ \t]+(?:(public|internal|private)[ \t]+)?(?:val|fun)[ \t]+([A-Za-z_]\w*)[ \t]*([^\n{=]*)",
        re.MULTILINE,
    )

    def owner_end(text: str, owner: re.Match[str]) -> int:
        depth = 0
        for offset in range(owner.end() - 1, len(text)):
            if text[offset] == "{":
                depth += 1
            elif text[offset] == "}":
                depth -= 1
                if depth == 0:
                    return offset
        return len(text)

    for path in paths:
        text = path.read_text(encoding="utf-8")
        allow_internal = path.resolve() in included_internal_paths and path.name.endswith("Fixtures.kt")
        owners = [(owner, owner_end(text, owner)) for owner in owner_pattern.finditer(text)]
        for match in member_pattern.finditer(text):
            owner_entry = next(
                (
                    (candidate, end)
                    for candidate, end in reversed(owners)
                    if candidate.end() <= match.start() < end
                ),
                None,
            )
            if owner_entry is None:
                continue
            owner, _ = owner_entry
            between = text[owner.end() : match.start()]
            if between.count("{") != between.count("}"):
                continue
            owner_visibility = owner.group(1) or "public"
            member_visibility = match.group(1) or "public"
            if member_visibility in ("internal", "private"):
                continue
            if owner_visibility == "private" or (owner_visibility == "internal" and not allow_internal):
                continue
            line = line_at(text, match.start()).strip()
            if not line:
                continue
            values.append(
                {
                    "name": match.group(2),
                    "signature": line,
                    "file": path.name,
                    "visibility": "internal" if owner_visibility == "internal" else "public",
                }
            )
    return sorted(values, key=lambda item: (item["name"], item["file"], item["signature"]))


def name_tokens(value: str) -> list[str]:
    stem = re.sub(r"(?:Cell|Screen|State|Model)$", "", value)
    parts = re.findall(r"[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)|\d+", stem)
    return [part.lower() for part in parts if len(part) > 2]


def command_gallery(repo: Repository, args: argparse.Namespace) -> int:
    graph = repo.graph(args.no_refresh)
    testkit_fixture_files = [
        path
        for node in graph["nodes"]
        if node.get("role") == "testkit"
        for path in kotlin_files(repo.root / node["projectDir"])
        if path.name.endswith("Fixtures.kt")
    ]
    shared_fixtures = fixture_declarations(testkit_fixture_files)
    modules = []
    for node in graph["nodes"]:
        if node.get("role") != "feature":
            continue
        api_files = repo.files_in_dirs(node.get("publicApiDirs", []))
        entries = composables(api_files)
        module_files = kotlin_files(repo.root / node["projectDir"])
        local_fixture_files = [path for path in module_files if path.name.endswith("Fixtures.kt")]
        fixtures = sorted(
            fixture_declarations(
                local_fixture_files,
                include_internal=local_fixture_files,
            )
            + shared_fixtures,
            key=lambda item: (item["name"], item["file"], item["signature"]),
        )
        cells = []
        for entry in entries:
            if not entry["cell"]:
                continue
            tokens = name_tokens(entry["name"])
            matches = [fixture for fixture in fixtures if any(token in (fixture["name"] + " " + fixture["signature"]).lower() for token in tokens)]
            cells.append(
                {
                    "name": entry["name"],
                    "fixtureStates": [
                        {
                            "file": fixture["file"],
                            "name": fixture["name"],
                            "visibility": fixture["visibility"],
                        }
                        for fixture in matches
                    ],
                    "missingFixtures": not matches,
                }
            )
        modules.append(
            {
                "module": node["path"],
                "screens": [entry["name"] for entry in entries if not entry["cell"]],
                "cells": cells,
            }
        )
    result = {"kind": "fixture-review-index", "rendersOrLaunches": False, "modules": modules}
    if args.json:
        dump_json(result)
    else:
        print("Helix fixture gallery index (index only; does not launch or render an app)")
        print("| Feature | Cell | Fixture states |")
        print("| --- | --- | --- |")
        for module in modules:
            if not module["cells"]:
                print(f"| {module['module']} | — | MISSING |")
            for cell in module["cells"]:
                states = (
                    ", ".join(
                        f"{fixture['file']}:{fixture['name']}"
                        + (" (internal)" if fixture["visibility"] == "internal" else "")
                        for fixture in cell["fixtureStates"]
                    )
                    if cell["fixtureStates"]
                    else "MISSING"
                )
                print(f"| {module['module']} | {cell['name']} | {states} |")
        print("Screens:")
        for module in modules:
            print(f"  {module['module']}: {', '.join(module['screens']) or 'none'}")
    return 0


def stage_contract(repo: Repository) -> tuple[str, dict[str, Any], dict[str, Any]]:
    source = (repo.root / "docs/architecture/helix-kmp-source-of-truth.md").read_text(encoding="utf-8")
    match = re.search(
        r"<!-- HELIX_CONTROL_PLANE_STAGES_BEGIN -->\s*```json\s*(.*?)\s*```\s*<!-- HELIX_CONTROL_PLANE_STAGES_END -->",
        source,
        re.DOTALL,
    )
    if not match:
        raise CliError("canonical control-plane stage block is missing")
    stages = json.loads(match.group(1))
    stage = (repo.root / "tooling/helix-kmp/STAGE").read_text(encoding="utf-8").strip()
    if stage not in stages:
        raise CliError(f"unknown control-plane stage {stage}")
    return stage, stages, stages[stage]


def generated_sections(repo: Repository) -> dict[str, str]:
    stage, stages, contract = stage_contract(repo)
    policy = json.loads((repo.root / "config/helix/dependency-policy.json").read_text(encoding="utf-8"))
    available = contract["availableCommands"]
    cli_help = subprocess.run(
        [str(repo.root / "tooling/helix-kmp/helix-kmp"), "help"],
        cwd=repo.root,
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    missing_help = [command for command in available if not re.search(rf"\bhelix-kmp\s+{re.escape(command)}\b", cli_help)]
    if missing_help:
        raise CliError(f"CLI help omits current-stage commands: {', '.join(missing_help)}")
    roles = list(policy.get("roles", {}).keys()) + ["testkit"]
    role_lines = ["| Path | Role | What lives there |", "| --- | --- | --- |"]
    for role in roles:
        if role not in ROLE_METADATA:
            raise CliError(f"missing generated role metadata for {role}")
        path, description = ROLE_METADATA[role]
        role_lines.append(f"| `{path}` | `{role}` | {description} |")
    workflow = [
        "```bash",
        "tooling/helix-kmp/helix-kmp context <target>",
        "tooling/helix-kmp/helix-kmp create feature <name> [--capability <name>]",
        "tooling/helix-kmp/helix-kmp create capability <name>",
        "tooling/helix-kmp/helix-kmp create cell <feature> <CellName>",
        "tooling/helix-kmp/helix-kmp verify --fast --affected",
        "tooling/helix-kmp/helix-kmp impact <target>",
        "tooling/helix-kmp/helix-kmp doctor [<scope>] --explain",
        "tooling/helix-kmp/helix-kmp graph [<module>]",
        "tooling/helix-kmp/helix-kmp gallery",
        "```",
    ]
    workflow = [line for line in workflow if not line.startswith("tooling/") or line.split()[1] in available]
    denied = lambda source, target: target not in policy["roles"][source]["allow"]
    prohibitions = []
    if denied("feature", "capability_impl"):
        prohibitions.append("- Feature -> Capability Impl: forbidden.")
    if denied("capability_impl", "capability_impl"):
        prohibitions.append("- Capability Impl -> another business Capability Impl: forbidden.")
    if all(denied("ui", role) for role in ("feature", "capability_api", "capability_impl")):
        prohibitions.append("- UI -> Feature / Capability API / Capability Impl: forbidden.")
    if all(denied("capability_api", role) for role in ("ui", "capability_impl", "foundation_runtime", "storage")):
        prohibitions.append("- Capability API -> UI / Capability Impl / Foundation Runtime / Storage: forbidden.")
    stage_order = [name for name in stages if re.fullmatch(r"P\d+", name)]
    later = stage_order[stage_order.index(stage) + 1 :]
    future_lines = ["| Command | Stage | Status |", "| --- | --- | --- |"]
    for later_stage in later:
        for command in stages[later_stage]["availableCommands"]:
            future_lines.append(f"| `helix-kmp {command} ...` | {later_stage} | Not built |")
    return {
        "stage": f"> **Adoption stage: control plane {stage}.** Available commands: {', '.join(f'`helix-kmp {item}`' for item in available)}.",
        "module-roles": "\n".join(role_lines),
        "workflow": "\n".join(workflow),
        "policy-prohibitions": "\n".join(prohibitions),
        "future-commands": "\n".join(future_lines),
    }


def render_agents(repo: Repository, original: str) -> str:
    result = original
    for name, content in generated_sections(repo).items():
        pattern = re.compile(
            rf"(<!-- helix:generated:{re.escape(name)} BEGIN -->).*?(<!-- helix:generated:{re.escape(name)} END -->)",
            re.DOTALL,
        )
        if not pattern.search(result):
            raise CliError(f"AGENTS.md is missing generated section markers for {name}")
        result = pattern.sub(rf"\1\n{content}\n\2", result)
    return result


def self_check_rules(repo: Repository) -> None:
    kotlin = (
        repo.root
        / "build-logic/convention/src/main/kotlin/dev/mayankmkh/basekmpproject/convention/validation/HelixGraphTasks.kt"
    ).read_text(encoding="utf-8")
    found = set(re.findall(r'"([A-Z][A-Z0-9]+(?:-[A-Z0-9]+)+)"', kotlin))
    found.update(re.findall(r"\[([A-Z][A-Z0-9-]+)\]", kotlin))
    policy = json.loads(
        (repo.root / "config/helix/dependency-policy.json").read_text(encoding="utf-8")
    )
    found.update(item["id"] for item in policy.get("conditionalAllows", []))
    found = {item for item in found if item in RULE_IDS}
    if found != set(RULE_IDS):
        raise CliError(f"Python/Kotlin rule ID drift: Python={sorted(RULE_IDS)}, Kotlin={sorted(found)}")


def command_agents(repo: Repository, args: argparse.Namespace) -> int:
    self_check_rules(repo)
    path = repo.root / "AGENTS.md"
    original = path.read_text(encoding="utf-8")
    rendered = render_agents(repo, original)
    stage, _, contract = stage_contract(repo)
    available = set(contract["availableCommands"])
    invalid_mentions = []
    for skill in sorted((repo.root / ".agents/skills").glob("*/SKILL.md")):
        text = skill.read_text(encoding="utf-8")
        for verb in re.findall(r"helix-kmp\s+([a-z][a-z-]*)", text):
            if verb not in available:
                invalid_mentions.append(f"{skill.relative_to(repo.root)}: helix-kmp {verb}")
    if invalid_mentions:
        raise CliError(f"Skills mention commands unavailable in {stage}: " + "; ".join(invalid_mentions))
    if original == rendered:
        print("Helix agent instructions: synchronized")
        return 0
    if args.apply:
        path.write_text(rendered, encoding="utf-8")
        print("Helix agent instructions: regenerated AGENTS.md")
        return 0
    diff = difflib.unified_diff(
        original.splitlines(keepends=True),
        rendered.splitlines(keepends=True),
        fromfile="AGENTS.md",
        tofile="AGENTS.md (generated)",
    )
    sys.stdout.writelines(diff)
    return 1


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(add_help=False)
    commands = result.add_subparsers(dest="command", required=True)
    graph = commands.add_parser("graph")
    graph.add_argument("module", nargs="?")
    graph.add_argument("--json", action="store_true")
    graph.add_argument("--no-refresh", action="store_true")
    graph.set_defaults(func=command_graph)
    impact = commands.add_parser("impact")
    impact.add_argument("target")
    impact.add_argument("--json", action="store_true")
    impact.add_argument("--no-refresh", action="store_true")
    impact.set_defaults(func=command_impact)
    doctor = commands.add_parser("doctor")
    doctor.add_argument("scope", nargs="?")
    doctor.add_argument("--explain", action="store_true")
    doctor.add_argument("--json", action="store_true")
    doctor.add_argument("--no-refresh", action="store_true")
    doctor.set_defaults(func=command_doctor)
    context = commands.add_parser("context")
    context.add_argument("target")
    context.add_argument("--json", action="store_true")
    context.add_argument("--no-refresh", action="store_true")
    context.add_argument("--files-only", action="store_true")
    context.set_defaults(func=command_context)
    gallery = commands.add_parser("gallery")
    gallery.add_argument("--json", action="store_true")
    gallery.add_argument("--no-refresh", action="store_true")
    gallery.set_defaults(func=command_gallery)
    agents = commands.add_parser("agents")
    agents.add_argument("--apply", action="store_true")
    agents.set_defaults(func=command_agents)
    return result


def main() -> int:
    args = parser().parse_args()
    repo = Repository(Path(os.environ.get("HELIX_KMP_REPO_ROOT", Path(__file__).resolve().parents[3])))
    try:
        return args.func(repo, args)
    except (CliError, OSError, json.JSONDecodeError) as error:
        print(f"helix-kmp: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
