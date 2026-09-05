package __PACKAGE__

import com.github.michaelbull.result.Ok
import __API_PACKAGE__.__NAME__Commands
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Queries
import __API_PACKAGE__.__NAME__Record
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

/** The only public declaration in this module. Load it from the app's composition root. */
public val __name__CapabilityModule: Module = module {
    // Replace this canned answer with the Ktor-backed source once the endpoint exists.
    single<__NAME__RemoteSource> { __NAME__RemoteSource { Ok(sampleRecords()) } }
    singleOf(::__NAME__LocalSource)
    single { __NAME__CapabilityImpl(get(), get(), get(), get()) } onClose { it?.close() }
    single<__NAME__Queries> { get<__NAME__CapabilityImpl>() }
    single<__NAME__Commands> { get<__NAME__CapabilityImpl>() }
}

private fun sampleRecords() = listOf(__NAME__Record(__NAME__Id("1"), "__name__ 1"))
