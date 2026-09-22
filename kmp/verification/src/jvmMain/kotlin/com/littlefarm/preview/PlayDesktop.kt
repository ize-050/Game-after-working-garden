package com.littlefarm.preview

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.littlefarm.App
import com.littlefarm.account.AccountKeyValueStore
import com.littlefarm.account.GardenSession
import com.littlefarm.account.UnavailableAccountPlatform
import com.littlefarm.feedback.GameFeedbackController
import com.littlefarm.platform.SaveStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Interactive local verification, not a shipped desktop/iOS app or connected Firebase client. */
fun main(args: Array<String>) {
    require(args.size <= 1) { "Expected only the isolated desktop-preview directory argument" }
    val directory = Path.of(args.firstOrNull() ?: ".tooling/desktop-preview").toAbsolutePath().normalize()
    require(directory.fileName?.toString() == "desktop-preview") {
        "Desktop QA saves must use a dedicated directory named desktop-preview"
    }
    require(!Files.isSymbolicLink(directory)) { "Preview storage must not be a symbolic link" }
    Files.createDirectories(directory)
    require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Preview storage is not a directory" }

    val guestStore = DesktopGuestSave(AtomicPreviewFile(directory, "guest-save.json"))
    val preferences = DesktopPreviewPreferences(AtomicPreviewFile(directory, "preferences.json"))
    val session = GardenSession(guestStore, preferences, UnavailableAccountPlatform())
    val feedback = GameFeedbackController(preferences, JavaSoundFarmAudio())
    feedback.setActive(false)
    println("Little Farm desktop QA — not iOS. Firebase is unavailable; Guest-only local play.")
    println("Isolated preview saves: $directory")

    try {
        application {
            Window(
                onCloseRequest = { feedback.close(); exitApplication() },
                title = "Little Farm · ทดลองบน Mac (ไม่ใช่ iOS)",
                state = rememberWindowState(width = 430.dp, height = 900.dp),
                resizable = true,
            ) {
                DisposableEffect(window) {
                    window.minimumSize = Dimension(320, 600)
                    fun updateActive() {
                        feedback.setActive(window.isFocused && (window.extendedState and Frame.ICONIFIED) == 0)
                    }
                    val focusListener = object : WindowAdapter() {
                        override fun windowGainedFocus(event: WindowEvent) = updateActive()
                        override fun windowLostFocus(event: WindowEvent) = updateActive()
                    }
                    val stateListener = WindowStateListener { updateActive() }
                    window.addWindowFocusListener(focusListener)
                    window.addWindowStateListener(stateListener)
                    updateActive()
                    onDispose {
                        window.removeWindowFocusListener(focusListener)
                        window.removeWindowStateListener(stateListener)
                        feedback.setActive(false)
                    }
                }
                App(guestStore, session = session, feedback = feedback)
            }
        }
    } finally {
        feedback.close()
    }
}

/** Only two fixed files can be addressed; key names never become filesystem paths. */
private class AtomicPreviewFile(private val directory: Path, name: String) {
    private val path = directory.resolve(name)

    init { require(name in setOf("guest-save.json", "preferences.json")) }

    private fun validateTarget() {
        require(!Files.isSymbolicLink(directory) && Files.isDirectory(directory, NOFOLLOW_LINKS)) {
            "Preview directory changed unexpectedly"
        }
        require(!Files.isSymbolicLink(path)) { "Preview save must not be a symbolic link" }
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Preview save is not a regular file" }
        }
    }

    @Synchronized
    fun read(): String? {
        validateTarget()
        if (!Files.exists(path, NOFOLLOW_LINKS)) return null
        require(Files.size(path) <= 1_048_576) { "Preview save is unexpectedly large" }
        return Files.readString(path)
    }

    @Synchronized
    fun write(value: String) {
        validateTarget()
        require(value.length <= 1_048_576) { "Preview save is unexpectedly large" }
        val temporary = Files.createTempFile(directory, ".preview-", ".tmp")
        try {
            Files.writeString(temporary, value)
            validateTarget()
            try {
                Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private class DesktopGuestSave(private val file: AtomicPreviewFile) : SaveStore {
    override fun read(): String? = file.read()
    override fun write(value: String) = file.write(value)
}

/** Includes device preferences and non-credential session metadata, isolated from mobile stores. */
private class DesktopPreviewPreferences(private val file: AtomicPreviewFile) : AccountKeyValueStore {
    private fun values(): Map<String, JsonPrimitive> {
        val raw = file.read() ?: return emptyMap()
        return Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) ->
            require(value is JsonPrimitive && value.isString) { "Invalid desktop preference value" }
            value
        }
    }

    @Synchronized
    override fun read(key: String): String? = values()[key]?.content

    @Synchronized
    override fun write(key: String, value: String) {
        file.write(JsonObject(values() + (key to JsonPrimitive(value))).toString())
    }

    @Synchronized
    override fun remove(key: String) {
        file.write(JsonObject(values() - key).toString())
    }
}
