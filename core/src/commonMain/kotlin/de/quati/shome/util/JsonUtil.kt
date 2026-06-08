package de.quati.shome.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun JsonObject.getArray(key: String) = this[key] as? JsonArray
fun JsonObject.getObject(key: String) = this[key] as? JsonObject
fun JsonObject.getPrimitive(key: String) = this[key] as? JsonPrimitive
val JsonPrimitive.stringOrNull: String? get() = if (isString) contentOrNull else null
