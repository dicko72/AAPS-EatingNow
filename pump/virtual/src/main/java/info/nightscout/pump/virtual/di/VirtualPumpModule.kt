package info.nightscout.pump.virtual.di

import app.aaps.core.interfaces.pump.VirtualPump
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.virtual.VirtualPumpFragment
import info.nightscout.pump.virtual.VirtualPumpPlugin

@Module(
    includes = [
        VirtualPumpModule.Bindings::class
    ]
)
@Suppress("unused")
abstract class VirtualPumpModule {

    @ContributesAndroidInjector abstract fun contributesVirtualPumpFragment(): VirtualPumpFragment

    @Module
    interface Bindings {

        @Binds fun bindVirtualPump(virtualPumpPlugin: VirtualPumpPlugin): VirtualPump
    }
}