package de.quati.shome

import kotlinx.browser.window

actual fun getHost(): String = window.location.hostname
actual fun getPort() = window.location.port.toIntOrNull()

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}
