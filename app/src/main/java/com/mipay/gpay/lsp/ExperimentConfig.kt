package com.mipay.gpay.lsp

object ExperimentConfig {
    private const val PROPERTY = "persist.debug.mipay_gpay.experiment"
    const val SYSTEM = "system"
    const val OFF = "off"
    private const val DEFAULT = SYSTEM

    private val validModes = setOf(SYSTEM, OFF)

    fun mode(): String {
        val raw = try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, PROPERTY, DEFAULT) as? String) ?: DEFAULT
        } catch (t: Throwable) {
            DevLog.e("read $PROPERTY failed", t)
            DEFAULT
        }
        return raw.lowercase().takeIf { it in validModes } ?: DEFAULT
    }

    fun system(mode: String): Boolean = mode == SYSTEM
}
