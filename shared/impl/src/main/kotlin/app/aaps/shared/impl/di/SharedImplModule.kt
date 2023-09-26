package app.aaps.shared.impl.di

import android.content.Context
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.AapsSchedulersImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.sharedPreferences.SPImplementation
import app.aaps.shared.impl.utils.DateUtilImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(
    includes = [
    ]
)
open class SharedImplModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SP = SPImplementation(PreferenceManager.getDefaultSharedPreferences(context), context)

    @Provides
    @Singleton
    fun provideL(sp: SP): L = LImpl(sp)

    @Provides
    @Singleton
    fun provideDateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @Singleton
    fun provideAAPSLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)

    @Provides
    @Singleton
    fun provideRxBus(aapsSchedulers: AapsSchedulers, aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsSchedulers, aapsLogger)

    @Provides
    @Singleton
    internal fun provideSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}