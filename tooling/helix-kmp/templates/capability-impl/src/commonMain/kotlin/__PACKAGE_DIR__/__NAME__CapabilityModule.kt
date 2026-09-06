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
    // Replace these canned answers with the Ktor-backed source once the endpoints exist.
    single<__NAME__RemoteSource> {
        object : __NAME__RemoteSource {
            override suspend fun fetchAll() = Ok(__name__Records())

            override suspend fun create(label: String) =
                Ok(Create__NAME__RemoteAnswer.Created(__NAME__Record(__NAME__Id("created"), label)))
        }
    }
    singleOf(::__NAME__LocalSource)
    singleOf(::__NAME__CapabilityImpl) onClose { it?.close() }
    // Aliases expose contracts only; onClose belongs to the implementation definition above.
    single<__NAME__Queries> { get<__NAME__CapabilityImpl>() }
    single<__NAME__Commands> { get<__NAME__CapabilityImpl>() }
}

private fun __name__Records() = listOf(__NAME__Record(__NAME__Id("1"), "__name__ 1"))
