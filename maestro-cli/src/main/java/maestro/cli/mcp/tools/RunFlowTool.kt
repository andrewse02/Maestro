package maestro.cli.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import maestro.MaestroException
import maestro.cli.session.MaestroSessionManager
import maestro.cli.util.WorkingDirectory
import maestro.cli.runner.CommandStatus
import maestro.orchestra.MaestroCommand
import maestro.orchestra.Orchestra
import maestro.orchestra.util.Env.withDefaultEnvVars
import maestro.orchestra.util.Env.withEnv
import maestro.orchestra.util.Env.withInjectedShellEnvVars
import maestro.orchestra.yaml.YamlCommandReader
import java.nio.file.Files
import java.util.IdentityHashMap

object RunFlowTool {
    private data class CommandExecutionResult(
        val index: Int,
        var description: String,
        var status: CommandStatus = CommandStatus.PENDING,
        var error: String? = null,
    )

    fun create(sessionManager: MaestroSessionManager): RegisteredTool {
        return RegisteredTool(
            Tool(
                name = "run_flow",
                description = """
                    Run Maestro commands on a device from either inline YAML or a flow file path.

                    Use this for ad hoc execution while exploring, debugging, or validating behavior.

                    Supported inputs:
                    - a full Maestro script with headers
                    - a list of commands
                    - a single command such as '- tapOn: 123'
                    - a path to an existing Maestro flow file

                    If this fails due to no device running, please ask the user to start a device!

                    Provide exactly one of `flow_yaml` or `flow_file`.

                    Syntax is validated as part of execution, so there is no need to run a separate syntax check first.

                    Examples of valid inputs:
                    ```
                    - tapOn: 123
                    ```

                    ```
                    appId: any
                    ---
                    - tapOn: 123
                    ```

                    ```
                    appId: any
                    # other headers here
                    ---
                    - tapOn: 456
                    - scroll
                    # other commands here
                    ```
                """.trimIndent(),
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        putJsonObject("device_id") {
                            put("type", "string")
                            put("description", "The ID of the device to run the flow on")
                        }
                        putJsonObject("flow_yaml") {
                            put("type", "string")
                            put("description", "YAML-formatted Maestro flow content to execute")
                        }
                        putJsonObject("flow_file") {
                            put("type", "string")
                            put("description", "Optional path to a Maestro flow file to execute. Relative paths are resolved from the MCP working directory.")
                        }
                        putJsonObject("env") {
                            put("type", "object")
                            put("description", "Optional environment variables to inject into the flow (e.g., {\"APP_ID\": \"com.example.app\", \"LANGUAGE\": \"en\"})")
                            putJsonObject("additionalProperties") {
                                put("type", "string")
                            }
                        }
                    },
                    required = listOf("device_id")
                )
            )
        ) { request ->
            try {
                val deviceId = request.arguments["device_id"]?.jsonPrimitive?.content
                val flowYaml = request.arguments["flow_yaml"]?.jsonPrimitive?.content
                val flowFile = request.arguments["flow_file"]?.jsonPrimitive?.content
                val envParam = request.arguments["env"]?.jsonObject

                if (deviceId == null) {
                    return@RegisteredTool CallToolResult(
                        content = listOf(TextContent("device_id is required")),
                        isError = true
                    )
                }

                if ((flowYaml == null) == (flowFile == null)) {
                    return@RegisteredTool CallToolResult(
                        content = listOf(TextContent("Provide exactly one of flow_yaml or flow_file")),
                        isError = true
                    )
                }

                val env = envParam?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

                val result = sessionManager.newSession(
                    host = null,
                    port = null,
                    driverHostPort = null,
                    deviceId = deviceId,
                    platform = null
                ) { session ->
                    val flowFileObj = flowFile?.let { WorkingDirectory.resolve(it) }
                    if (flowFileObj != null && !flowFileObj.exists()) {
                        error("Flow file not found: ${flowFileObj.absolutePath}")
                    }

                    val tempFile = if (flowYaml != null) {
                        Files.createTempFile("maestro-flow", ".yaml").toFile().apply {
                            writeText(flowYaml)
                        }
                    } else {
                        null
                    }
                    val sourceFile = flowFileObj ?: tempFile ?: error("No flow source provided")

                    try {
                        val commands = YamlCommandReader.readCommands(sourceFile.toPath())
                        val finalEnv = env
                            .withInjectedShellEnvVars()
                            .withDefaultEnvVars(sourceFile, deviceId)
                        val commandsWithEnv = commands.withEnv(finalEnv)

                        val commandResults = IdentityHashMap<MaestroCommand, CommandExecutionResult>()

                        fun commandResult(index: Int, command: MaestroCommand): CommandExecutionResult {
                            return commandResults.getOrPut(command) {
                                CommandExecutionResult(
                                    index = index,
                                    description = command.description(),
                                )
                            }
                        }

                        val orchestra = Orchestra(
                            maestro = session.maestro,
                            onCommandStart = { index, command ->
                                commandResult(index, command).apply {
                                    description = command.description()
                                    status = CommandStatus.RUNNING
                                }
                            },
                            onCommandComplete = { index, command ->
                                commandResult(index, command).apply {
                                    description = command.description()
                                    status = CommandStatus.COMPLETED
                                }
                            },
                            onCommandWarned = { index, command ->
                                commandResult(index, command).apply {
                                    description = command.description()
                                    status = CommandStatus.WARNED
                                }
                            },
                            onCommandSkipped = { index, command ->
                                commandResult(index, command).apply {
                                    description = command.description()
                                    status = CommandStatus.SKIPPED
                                }
                            },
                            onCommandMetadataUpdate = { command, metadata ->
                                commandResults[command]?.let { result ->
                                    result.description = metadata.evaluatedCommand?.description() ?: command.description()
                                }
                            },
                            onCommandFailed = { index, command, throwable ->
                                commandResult(index, command).apply {
                                    description = command.description()
                                    status = CommandStatus.FAILED
                                    error = throwable.message
                                }
                                Orchestra.ErrorResolution.FAIL
                            }
                        )

                        val flowSuccess = try {
                            runBlocking {
                                orchestra.runFlow(commandsWithEnv)
                            }
                        } catch (e: MaestroException) {
                            false
                        }

                        val orderedResults = commandResults.values.sortedBy { it.index }

                        buildJsonObject {
                            put("success", flowSuccess)
                            put("device_id", deviceId)
                            put("commands_executed", commands.size)
                            put("source", sourceFile.absolutePath)
                            put(
                                "message",
                                if (flowSuccess) "Flow executed successfully" else "Flow execution failed"
                            )
                            putJsonArray("commands") {
                                orderedResults.forEach { result ->
                                    add(buildJsonObject {
                                        put("index", result.index)
                                        put("description", result.description)
                                        put("status", result.status.name)
                                        result.error?.let { put("error", it) }
                                    })
                                }
                            }
                            if (finalEnv.isNotEmpty()) {
                                putJsonObject("env_vars") {
                                    finalEnv.forEach { (key, value) ->
                                        put(key, value)
                                    }
                                }
                            }
                        }.toString()
                    } finally {
                        tempFile?.delete()
                    }
                }

                CallToolResult(
                    content = listOf(TextContent(result)),
                    isError = result.contains("\"success\":false")
                )
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("Failed to run flow: ${e.message}")),
                    isError = true
                )
            }
        }
    }
}
