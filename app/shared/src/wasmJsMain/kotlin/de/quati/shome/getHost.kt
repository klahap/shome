package de.quati.shome

import kotlinx.browser.window

actual fun getHost(): String = window.location.hostname
