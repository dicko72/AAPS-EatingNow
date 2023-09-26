package app.aaps.core.interfaces.source

interface DexcomBoyda {

    fun isEnabled(): Boolean
    fun requestPermissionIfNeeded()
    fun findDexcomPackageName(): String?
}