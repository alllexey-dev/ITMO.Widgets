package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The custom-services opt-in of one test. [setEnabled] records the request in [requests], fails while [failing],
 * otherwise stores the value and reports it to [onSet].
 */
class FakeCustomServicesRepository(
    enabled: Boolean = false,
    private val onSet: (Boolean) -> Unit = {},
) : CustomServicesRepository {
    val enabled = MutableStateFlow(enabled)
    val requests = mutableListOf<Boolean>()
    var failing = false

    override fun observeEnabled(): Flow<Boolean> = enabled

    override suspend fun isEnabled(): Boolean = enabled.value

    override suspend fun setEnabled(enabled: Boolean) {
        requests += enabled
        if (failing) error("Opt-in is unavailable in this fixture")
        this.enabled.value = enabled
        onSet(enabled)
    }
}
