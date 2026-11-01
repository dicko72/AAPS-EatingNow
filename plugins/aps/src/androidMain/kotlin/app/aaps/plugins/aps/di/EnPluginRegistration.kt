// Modified for Eating Now
package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.plugins.aps.EN.ENPlugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

/**
 * Registers the Eating Now APS plugin.
 *
 * ENPlugin lives in androidMain (it uses Android/Compose APIs and is Android-only), so its registration
 * stays here rather than in [ApsPluginRegistrations] (commonMain). Key 235 sits between openAPS AutoISF
 * (230) and Autotune (240). Merged unqualified, matching the other APS plugin registrations.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object EnPluginRegistration {

    @Provides
    @IntoMap
    @IntKey(235)
    fun eatingNowEntry(plugin: ENPlugin): PluginBase = plugin
}
