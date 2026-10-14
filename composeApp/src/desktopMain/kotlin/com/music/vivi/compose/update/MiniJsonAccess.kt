package com.music.vivi.compose.update

internal fun Any?.asObject(): Map<String, Any?> = (this as? Map<*, *>)?.entries
    ?.associate { (k, v) -> k.toString() to v }
    ?: throw IllegalArgumentException("Expected a JSON object")

internal fun Any?.asArray(): List<Any?> = this as? List<Any?> ?: throw IllegalArgumentException("Expected a JSON array")

internal fun Map<String, Any?>.string(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.requiredString(key: String): String =
    string(key) ?: throw IllegalArgumentException("Missing string field '$key'")
