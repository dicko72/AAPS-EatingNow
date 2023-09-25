// Modified for Eating Now
package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.plugins.aps.APSResultObject
import app.aaps.plugins.aps.OpenAPSFragment
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.loop.LoopPlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.plugins.aps.loop.LoopVariantPreference

@Module(
    includes = [
        AutotuneModule::class,
        AlgModule::class,
        LoopModule::class,
        ApsModule.Bindings::class
    ]
)

@Suppress("unused")
abstract class ApsModule {

    @ContributesAndroidInjector abstract fun contributesOpenAPSFragment(): OpenAPSFragment
    @ContributesAndroidInjector abstract fun apsResultInjector(): APSResultObject
    @ContributesAndroidInjector abstract fun loopVariantPreferenceInjector(): LoopVariantPreference

    @Module
    interface Bindings {

        @Binds fun bindLoop(loopPlugin: LoopPlugin): Loop
        @Binds fun bindAutotune(autotunePlugin: AutotunePlugin): Autotune
    }
}