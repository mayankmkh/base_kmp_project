package dev.mayankmkh.basekmpproject.convention.validation

import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.time.LocalDate
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private const val FIELD_SEPARATOR = "\u001f"

internal fun encodeNode(
    path: String,
    roles: List<String>,
    projectDir: String,
    relativeProjectDir: String,
    targets: List<String>,
): String =
    listOf(path, roles.joinToString(","), projectDir, relativeProjectDir, targets.joinToString(","))
        .joinToString(FIELD_SEPARATOR)

internal fun encodeEdge(from: String, to: String, configuration: String): String =
    listOf(from, to, configuration).joinToString(FIELD_SEPARATOR)

private data class Node(
    val path: String,
    val roles: List<String>,
    val projectDir: File,
    val relativeProjectDir: String,
    val targets: List<String>,
) {
    val role: String?
        get() = roles.singleOrNull()
}

private data class Edge(val from: String, val to: String, val configuration: String)

private data class Finding(
    val rule: String,
    val subject: String,
    val problem: String,
    val fix: String,
    val severity: String = "error",
) {
    fun line(): String = "[$rule] $subject -- $problem. Fix: $fix"
}

private data class ArchitectureException(
    val rule: String,
    val scope: String,
    val owner: String,
    val reason: String,
    val expires: LocalDate,
)

@CacheableTask
abstract class CheckModuleGraphTask : DefaultTask() {
    @get:Input abstract val nodeRecords: ListProperty<String>

    @get:Input abstract val edgeRecords: ListProperty<String>

    @get:Input abstract val currentDate: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exceptionsFile: RegularFileProperty

    /**
     * The modules' `src` roots, not the `.kt` files inside them.
     *
     * Directories are what makes the source rules see a file that did not exist when the build was
     * configured. Gradle expands a directory in an `@InputFiles` collection while fingerprinting,
     * so adding a file changes the fingerprint and the task re-runs; the task action then walks the
     * same directories, so it reads exactly what was fingerprinted. A collection of file *trees*
     * resolved during configuration cannot do either under the configuration cache: on a cache hit
     * the configuration block never runs again, the stored file set is the one from the run that
     * populated it, and a newly added declaration slips past `FEATURE-PUBLIC-SURFACE-OUTSIDE-API`
     * and the import-based feature rule until something else forces reconfiguration.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @get:OutputFile abstract val reportFile: RegularFileProperty

    @TaskAction
    fun checkGraph() {
        val nodes =
            nodeRecords.get().map { record ->
                val fields = record.split(FIELD_SEPARATOR)
                Node(
                    path = fields[0],
                    roles = fields[1].takeIf(String::isNotEmpty)?.split(',').orEmpty(),
                    projectDir = File(fields[2]),
                    relativeProjectDir = fields[3],
                    targets = fields[4].takeIf(String::isNotEmpty)?.split(',').orEmpty(),
                )
            }
        val nodeByPath = nodes.associateBy(Node::path)
        val edges =
            edgeRecords.get().map { record ->
                val fields = record.split(FIELD_SEPARATOR)
                Edge(fields[0], fields[1], fields[2])
            }
        val sources =
            sourceDirectories.files
                .filter(File::isDirectory)
                .flatMap { root -> root.walkTopDown().filter(File::isFile).toList() }
                .filter(File::isMainKotlinSource)
                .sortedBy(File::getAbsolutePath)
        val sourcesByModule = nodes.associate { node ->
            node.path to
                sources.filter { source ->
                    source.toPath().normalize().startsWith(node.projectDir.toPath().normalize())
                }
        }

        val policy = parseObject(policyFile.get().asFile)
        val findings = mutableListOf<Finding>()
        validateRolesAndPaths(nodes, findings)
        validateEdges(edges, nodeByPath, sourcesByModule, policy, findings)
        validateFeatureSurface(nodes, sourcesByModule, findings)
        validateCycles(edges, nodes.map(Node::path), findings)

        val today = LocalDate.parse(currentDate.get())
        val exceptions = readExceptions(exceptionsFile.get().asFile)
        exceptions
            .filter { it.expires < today }
            .forEach { exception ->
                findings +=
                    Finding(
                        rule = "EXC-EXPIRED",
                        subject = exception.scope,
                        problem =
                            "exception for ${exception.rule} owned by ${exception.owner} expired on ${exception.expires}",
                        fix =
                            "remove the exception or renew it with owner approval and a new removal condition",
                    )
            }

        val activeExceptions = exceptions.filterNot { it.expires < today }
        val effectiveFindings = findings.map { finding ->
            val exception = activeExceptions.firstOrNull {
                it.rule == finding.rule && it.scope == finding.subject
            }
            if (exception == null || finding.rule == "EXC-EXPIRED") {
                finding
            } else {
                finding.copy(
                    problem =
                        "${finding.problem} (temporary exception: ${exception.reason}; owner: ${exception.owner}; expires: ${exception.expires})",
                    severity = "warning",
                )
            }
        }

        effectiveFindings.forEach { finding ->
            if (finding.severity == "warning") logger.warn(finding.line())
            else logger.error(finding.line())
        }
        writeReport(nodes, edges, effectiveFindings)

        val failures = effectiveFindings.count { it.severity == "error" }
        if (failures > 0) {
            throw GradleException("Helix module graph validation failed with $failures finding(s).")
        }
    }

    private fun validateRolesAndPaths(nodes: List<Node>, findings: MutableList<Finding>) {
        nodes
            .filterNot { ignoredForRoleValidation(it.path) }
            .forEach { node ->
                when {
                    node.roles.isEmpty() ->
                        findings +=
                            Finding(
                                "MOD-ROLE-MISSING",
                                node.path,
                                "module has no Helix role convention plugin",
                                "apply the one bkp.kmp.<role> plugin matching this module path",
                            )
                    node.roles.size > 1 ->
                        findings +=
                            Finding(
                                "MOD-ROLE-MULTIPLE",
                                node.path,
                                "module records multiple Helix roles: ${node.roles.joinToString()}",
                                "keep exactly one Helix role convention plugin",
                            )
                    !roleMatchesPath(node.roles.single(), node.path) ->
                        findings +=
                            Finding(
                                "MOD-PATH-ROLE-MISMATCH",
                                node.path,
                                "role ${node.roles.single()} does not match the module path",
                                "move the module or apply the role plugin prescribed by the Helix path table",
                            )
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateEdges(
        edges: List<Edge>,
        nodeByPath: Map<String, Node>,
        sourcesByModule: Map<String, List<File>>,
        policy: Map<String, Any?>,
        findings: MutableList<Finding>,
    ) {
        val policyRoles = policy["roles"] as? Map<String, Any?> ?: emptyMap()
        val conditionalAllows =
            (policy["conditionalAllows"] as? List<*>).orEmpty().mapNotNull {
                it as? Map<String, Any?>
            }
        conditionalAllows.forEach { conditionalAllow ->
            val id = conditionalAllow["id"] as? String ?: "<missing id>"
            val predicate = conditionalAllow["predicate"] as? Map<String, Any?>
            val type = predicate?.get("type") as? String
            if (type != "target_public_api_and_source_import") {
                throw GradleException(
                    "Unknown Helix conditional allow predicate type '$type' for $id"
                )
            }
        }
        edges.forEach { edge ->
            val from = nodeByPath[edge.from] ?: return@forEach
            val to = nodeByPath[edge.to] ?: return@forEach
            val fromRole = from.role ?: return@forEach
            val toRole = to.role ?: return@forEach
            val subject = "${edge.from} -> ${edge.to}"

            // The canonical policy governs runtime/application edges. Testkit is a producer of
            // test-only artifacts, not a runtime role; consumers remain constrained to test source
            // configurations by dependency extraction and therefore never create graph edges.
            if (fromRole == "testkit") return@forEach

            // `:app:*` is one composition root spread across a shared KMP module and the
            // target-specific shells the master source lists under the same role (8.2). The Section
            // 9.0 matrix governs edges *between* roles, so an app→app edge is inside the role
            // rather than denied by it; `GRAPH-CYCLE-PHYSICAL` still holds the shells to a DAG.
            if (fromRole == "app" && toRole == "app") return@forEach

            val conditionalAllow = conditionalAllows.firstOrNull {
                it["from"] == fromRole && it["to"] == toRole
            }
            if (conditionalAllow != null) {
                // Predicate types were validated above; today's only type checks that the source
                // imports nothing from the target outside the target's public package.
                val predicate = conditionalAllow.getValue("predicate") as Map<String, Any?>
                val importSuffix =
                    predicate.getValue("sourceMayImportOnlyTargetPackageSuffix") as String
                val targetRoot = targetPackageRoot(sourcesByModule.getValue(to.path))
                val offendingImports =
                    if (targetRoot == null) {
                        listOf("target package root could not be determined")
                    } else {
                        val allowedPackage = "$targetRoot$importSuffix"
                        importsFromTarget(sourcesByModule.getValue(from.path), targetRoot)
                            .filterNot { imported ->
                                imported == allowedPackage ||
                                    imported.startsWith("$allowedPackage.")
                            }
                    }
                if (offendingImports.isNotEmpty()) {
                    findings +=
                        Finding(
                            conditionalAllow.getValue("id") as String,
                            subject,
                            "feature dependency imports target internals: ${offendingImports.joinToString()}",
                            "expose presentation contracts below the target $importSuffix package and import only that package",
                        )
                }
                return@forEach
            }

            val fromPolicy = policyRoles[fromRole] as? Map<String, Any?>
            val allowed =
                (fromPolicy?.get("allow") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (toRole !in allowed) {
                findings +=
                    Finding(
                        "DEP-ROLE-DENIED",
                        subject,
                        "$fromRole may not depend on $toRole under the default-deny role policy",
                        "depend on an allowed API role or move the implementation wiring to an app module",
                    )
            }
        }
    }

    private fun validateFeatureSurface(
        nodes: List<Node>,
        sourcesByModule: Map<String, List<File>>,
        findings: MutableList<Finding>,
    ) {
        nodes
            .filter { it.role == "feature" }
            .forEach { node ->
                sourcesByModule.getValue(node.path).forEach sourceLoop@{ source ->
                    if (source.isGenerated() || source.isInApiDirectory(node.projectDir)) {
                        return@sourceLoop
                    }
                    source.readLines().forEachIndexed { index, line ->
                        if (PUBLIC_TOP_LEVEL.matches(line)) {
                            findings +=
                                Finding(
                                    "FEATURE-PUBLIC-SURFACE-OUTSIDE-API",
                                    node.path,
                                    "${source.relativeTo(node.projectDir).invariantSeparatorsPath}:${index + 1} declares public feature surface outside api/",
                                    "move the declaration under an api/ directory or mark it internal/private",
                                )
                        }
                    }
                }
            }
    }

    private fun validateCycles(
        edges: List<Edge>,
        paths: List<String>,
        findings: MutableList<Finding>,
    ) {
        findings +=
            cycleFindings(
                paths.toSet(),
                edges,
                "GRAPH-CYCLE-PHYSICAL",
                "main-source project dependencies form a physical cycle",
                "reverse or remove an edge so dependencies remain acyclic",
            )

        val logicalEdges =
            edges
                .map { Edge(logicalPath(it.from), logicalPath(it.to), it.configuration) }
                .filter { it.from != it.to }
                .distinct()
        val logicalNodes = paths.map(::logicalPath).toSet()
        findings +=
            cycleFindings(
                logicalNodes,
                logicalEdges,
                "GRAPH-CYCLE-LOGICAL",
                "dependencies cycle after api/impl family modules are collapsed",
                "remove the cross-family back edge or introduce a lower stable contract",
            )
    }

    private fun cycleFindings(
        nodes: Set<String>,
        edges: List<Edge>,
        rule: String,
        problem: String,
        fix: String,
    ): List<Finding> =
        stronglyConnected(nodes, edges).map { cycle ->
            val subject = cycle.sorted().let { it.joinToString(" -> ") + " -> " + it.first() }
            Finding(rule, subject, problem, fix)
        }

    private fun writeReport(nodes: List<Node>, edges: List<Edge>, findings: List<Finding>) {
        val report =
            linkedMapOf(
                "schema" to 2,
                "nodes" to
                    nodes.sortedBy(Node::path).map {
                        linkedMapOf(
                            "path" to it.path,
                            "role" to it.role,
                            "roles" to it.roles,
                            "projectDir" to it.relativeProjectDir,
                            "targets" to it.targets.sorted(),
                            "publicApiDirs" to publicApiDirs(it),
                        )
                    },
                "edges" to
                    edges.sortedWith(compareBy(Edge::from, Edge::to, Edge::configuration)).map {
                        linkedMapOf(
                            "from" to it.from,
                            "to" to it.to,
                            "configuration" to it.configuration,
                        )
                    },
                "findings" to
                    findings.map {
                        linkedMapOf(
                            "rule" to it.rule,
                            "subject" to it.subject,
                            "problem" to it.problem,
                            "fix" to it.fix,
                            "severity" to it.severity,
                        )
                    },
            )
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
    }

    private fun publicApiDirs(node: Node): List<String> {
        val sourceRoot = node.projectDir.resolve("src")
        if (!sourceRoot.isDirectory) return emptyList()
        val relativeDirs =
            when (node.role) {
                "feature" ->
                    sourceRoot
                        .walkTopDown()
                        .filter(File::isDirectory)
                        .filter { directory ->
                            directory.name == "api" &&
                                MAIN_SOURCE_DIRECTORY.containsMatchIn(
                                    directory.relativeTo(node.projectDir).invariantSeparatorsPath
                                )
                        }
                        .toList()
                "capability_api",
                "foundation_api",
                "ui" ->
                    sourceRoot.listFiles().orEmpty().filter(File::isDirectory).filter {
                        it.name.endsWith("Main")
                    }
                else -> emptyList()
            }
        return relativeDirs
            .map { directory ->
                listOf(
                        node.relativeProjectDir,
                        directory.relativeTo(node.projectDir).invariantSeparatorsPath,
                    )
                    .filter(String::isNotEmpty)
                    .joinToString("/")
            }
            .distinct()
            .sorted()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readExceptions(file: File): List<ArchitectureException> {
        val root = parseObject(file)
        val entries = root["exceptions"] as? List<Map<String, Any?>> ?: emptyList()
        return entries.map {
            ArchitectureException(
                rule = it.getValue("rule") as String,
                scope = it.getValue("scope") as String,
                owner = it.getValue("owner") as String,
                reason = it.getValue("reason") as String,
                expires = LocalDate.parse(it.getValue("expires") as String),
            )
        }
    }

    private fun targetPackageRoot(sources: List<File>): String? {
        val packages =
            sources
                .filter { it.invariantSeparatorsPath.contains("/src/commonMain/kotlin/") }
                .mapNotNull { source ->
                    source.useLines { lines ->
                        lines.firstNotNullOfOrNull { line ->
                            PACKAGE.matchEntire(line)?.groupValues?.get(1)
                        }
                    }
                }
                .map { it.split('.') }
        if (packages.isEmpty()) return null
        val shortest = packages.minOf(List<String>::size)
        val common =
            (0 until shortest)
                .takeWhile { index -> packages.map { it[index] }.distinct().size == 1 }
                .map { packages.first()[it] }
        if (common.isEmpty()) return null
        val apiIndex = common.indexOf("api")
        val root = if (apiIndex >= 0) common.take(apiIndex) else common
        return root.joinToString(".").ifEmpty { null }
    }

    private fun importsFromTarget(sources: List<File>, targetRoot: String): List<String> =
        sources
            .filterNot { it.isGenerated() }
            .flatMap { source ->
                source.readLines().mapNotNull { line ->
                    IMPORT.matchEntire(line)?.groupValues?.get(1)
                }
            }
            .filter { it == targetRoot || it.startsWith("$targetRoot.") }
            .distinct()
            .sorted()

    private fun File.isGenerated(): Boolean {
        val normalized = invariantSeparatorsPath
        return "/generated/" in normalized || "/build/" in normalized
    }

    private fun File.isInApiDirectory(projectDir: File): Boolean {
        val relative = relativeTo(projectDir).invariantSeparatorsPath
        return relative.split('/').dropLast(1).contains("api")
    }

    companion object {
        private val PACKAGE = Regex("^package\\s+([A-Za-z_][\\w.]*)\\s*$")
        private val IMPORT =
            Regex("^import\\s+([A-Za-z_][\\w.]*(?:\\.\\*)?)(?:\\s+as\\s+\\w+)?\\s*$")
        private val PUBLIC_TOP_LEVEL =
            Regex(
                "^(?:(?:public|expect|actual|open|abstract|final|const|lateinit|inline|suspend|operator|infix|tailrec|external|value)\\s+)*(?:data\\s+class|enum\\s+class|sealed\\s+(?:class|interface)|class|interface|object|fun|val|var|typealias)\\b.*"
            )
        private val MAIN_SOURCE_DIRECTORY = Regex("^src/[^/]*Main(?:/|$)")

        // Structural parents such as `:app` or `:capability` exist as Gradle projects without a
        // build script; they are containers rather than modules and are filtered out before the
        // records reach this task. `:tooling:*` is developer tooling, outside the runtime graph.
        private fun ignoredForRoleValidation(path: String): Boolean = path.startsWith(":tooling:")

        private fun roleMatchesPath(role: String, path: String): Boolean =
            HelixRole.entries.firstOrNull { it.policyName == role }?.pathPattern?.matches(path) ==
                true

        private fun logicalPath(path: String): String =
            if (path.startsWith(":capability:") || path.startsWith(":platform:")) {
                path.removeSuffix("-api").removeSuffix("-impl")
            } else {
                path
            }

        private fun stronglyConnected(nodes: Set<String>, edges: List<Edge>): List<Set<String>> {
            val adjacency = nodes.associateWith { mutableSetOf<String>() }.toMutableMap()
            edges.forEach { adjacency.getOrPut(it.from, ::mutableSetOf).add(it.to) }
            var nextIndex = 0
            val indices = mutableMapOf<String, Int>()
            val lowLinks = mutableMapOf<String, Int>()
            val stack = ArrayDeque<String>()
            val onStack = mutableSetOf<String>()
            val components = mutableListOf<Set<String>>()

            fun visit(node: String) {
                indices[node] = nextIndex
                lowLinks[node] = nextIndex
                nextIndex++
                stack.addLast(node)
                onStack += node

                adjacency[node].orEmpty().sorted().forEach { neighbour ->
                    if (neighbour !in indices) {
                        visit(neighbour)
                        lowLinks[node] =
                            minOf(lowLinks.getValue(node), lowLinks.getValue(neighbour))
                    } else if (neighbour in onStack) {
                        lowLinks[node] = minOf(lowLinks.getValue(node), indices.getValue(neighbour))
                    }
                }

                if (lowLinks[node] == indices[node]) {
                    val component = mutableSetOf<String>()
                    do {
                        val member = stack.removeLast()
                        onStack -= member
                        component += member
                    } while (member != node)
                    val hasSelfEdge =
                        component.size == 1 &&
                            edges.any {
                                it.from == component.single() && it.to == component.single()
                            }
                    if (component.size > 1 || hasSelfEdge) components += component
                }
            }

            nodes.sorted().forEach { if (it !in indices) visit(it) }
            return components
        }
    }
}

@CacheableTask
abstract class CheckHelixPolicySyncTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceOfTruthFile: RegularFileProperty

    @get:OutputFile abstract val markerFile: RegularFileProperty

    @TaskAction
    fun checkSync() {
        val source = sourceOfTruthFile.get().asFile.readText()
        val block = POLICY_BLOCK.find(source)?.groupValues?.get(1)?.trim()
        val json = block?.removePrefix("```json")?.removeSuffix("```")?.trim()
        val inFile = runCatching { parseObject(policyFile.get().asFile) }.getOrNull()
        val inDocs = json?.let { runCatching { JsonSlurper().parseText(it) }.getOrNull() }
        if (inFile == null || inDocs == null || inFile != inDocs) {
            val line =
                "[POLICY-DRIFT] config/helix/dependency-policy.json -- policy differs from the normative Section 9.0 JSON. Fix: copy the marked JSON block from the Helix source of truth verbatim"
            logger.error(line)
            throw GradleException("Helix policy synchronization failed with 1 finding(s).")
        }
        val marker = markerFile.get().asFile
        marker.parentFile.mkdirs()
        marker.writeText("policy synchronized\n")
    }

    companion object {
        private val POLICY_BLOCK =
            Regex(
                "<!-- HELIX_DEPENDENCY_POLICY_BEGIN -->(.*?)<!-- HELIX_DEPENDENCY_POLICY_END -->",
                RegexOption.DOT_MATCHES_ALL,
            )
    }
}

@Suppress("UNCHECKED_CAST")
private fun parseObject(file: File): Map<String, Any?> =
    JsonSlurper().parse(file) as Map<String, Any?>

private val MAIN_KOTLIN_SOURCE = Regex("/src/[^/]*Main/kotlin/")

/**
 * The same selection the source rules used when they read a pre-resolved file tree: Kotlin files in
 * a `*Main` source set, never generated or build output.
 */
private fun File.isMainKotlinSource(): Boolean {
    val normalized = invariantSeparatorsPath
    return extension == "kt" &&
        MAIN_KOTLIN_SOURCE.containsMatchIn(normalized) &&
        "/generated/" !in normalized &&
        "/build/" !in normalized
}
