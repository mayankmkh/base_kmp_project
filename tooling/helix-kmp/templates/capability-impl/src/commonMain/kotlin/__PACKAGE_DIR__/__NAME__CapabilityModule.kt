package __PACKAGE__

import __API_PACKAGE__.__NAME__Commands
import __API_PACKAGE__.__NAME__Queries
import org.koin.core.module.Module
import org.koin.dsl.module

/** The only public declaration in this module. Load it from the app's composition root. */
public val __name__CapabilityModule: Module = module {
    single { __NAME__CapabilityImpl() }
    single<__NAME__Queries> { get<__NAME__CapabilityImpl>() }
    single<__NAME__Commands> { get<__NAME__CapabilityImpl>() }
}
