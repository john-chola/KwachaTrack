package zm.kwachatrack.core.domain

import org.junit.jupiter.api.Test
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.usecase.TransactionFilter
import kotlin.test.assertEquals

class TransactionFilterTest {
    private val cats = SystemCategories.all.associateBy { it.id }
    private val list =
        listOf(
            tx("A1", "John Banda", cat = SystemCategories.FAMILY, ts = 1),
            tx("A2", "ZESCO", cat = SystemCategories.UTILITIES, ts = 86_400_000).copy(provider = Provider.AIRTEL),
            tx("A3", null, cat = SystemCategories.FOOD, ts = 2),
        )

    @Test
    fun `filters by text, provider and category`() {
        assertEquals(list, TransactionFilter().apply(list, cats))
        assertEquals(listOf("A1"), TransactionFilter(query = "banda").apply(list, cats).map { it.providerTxnId })
        assertEquals(listOf("A2"), TransactionFilter(query = "utilit").apply(list, cats).map { it.providerTxnId })
        assertEquals(listOf("A2"), TransactionFilter(providers = setOf(Provider.AIRTEL)).apply(list, cats).map { it.providerTxnId })
        assertEquals(listOf("A3"), TransactionFilter(categoryId = SystemCategories.FOOD).apply(list, cats).map { it.providerTxnId })
    }

    @Test
    fun `groups newest day first`() {
        val groups = TransactionFilter.groupByDay(list)
        assertEquals(2, groups.size)
        assertEquals(listOf("A2"), groups.first().second.map { it.providerTxnId })
        assertEquals(listOf("A3", "A1"), groups.last().second.map { it.providerTxnId })
    }
}
