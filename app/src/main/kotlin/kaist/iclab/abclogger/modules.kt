package kaist.iclab.abclogger

import kaist.iclab.abclogger.collector.*
import kaist.iclab.abclogger.ui.config.ConfigViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val collectorModule = module {
    single(createdAtStart = false) { ActivityCollector(androidContext()) }
    single(createdAtStart = false) { AppUsageCollector(androidContext()) }
    single(createdAtStart = false) { BatteryCollector(androidContext()) }
    single(createdAtStart = false) { CallLogCollector(androidContext()) }
    single(createdAtStart = false) { DataTrafficCollector(androidContext()) }
    single(createdAtStart = false) { DeviceEventCollector(androidContext()) }
    single(createdAtStart = false) { InstalledAppCollector(androidContext()) }
    single(createdAtStart = false) { LocationCollector(androidContext()) }
    single(createdAtStart = false) { MediaCollector(androidContext()) }
    single(createdAtStart = false) { MessageCollector(androidContext()) }
    single(createdAtStart = false) { NotificationCollector() }
    single(createdAtStart = false) { PhysicalStatusCollector(androidContext()) }
    single(createdAtStart = false) { PolarH10Collector(androidContext()) }
    single(createdAtStart = false) { SurveyCollector(androidContext()) }
    single(createdAtStart = false) { WifiCollector(androidContext()) }
}

val viewModelModules = module {
    viewModel { ConfigViewModel(androidContext()) }
}