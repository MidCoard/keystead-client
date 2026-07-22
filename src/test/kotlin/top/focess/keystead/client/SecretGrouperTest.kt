package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretGrouperTest {

    @Test
    fun noneModeProducesNoGroups() {
        val secrets = listOf(secret("1", type = "LOGIN_PASSWORD"))
        assertTrue(SecretGrouper.group(secrets, SecretGroupingMode.NONE).isEmpty())
    }

    @Test
    fun emptyListProducesNoGroups() {
        assertTrue(SecretGrouper.group(emptyList(), SecretGroupingMode.TYPE).isEmpty())
    }

    @Test
    fun groupsByTypeWithSortedLabels() {
        val secrets =
            listOf(
                secret("1", type = "MFA_SECRET"),
                secret("2", type = "LOGIN_PASSWORD"),
                secret("3", type = "MFA_SECRET"),
            )
        val groups = SecretGrouper.group(secrets, SecretGroupingMode.TYPE)
        assertEquals(2, groups.size)
        // Sorted by type key: LOGIN_PASSWORD before MFA_SECRET.
        assertEquals("Login", groups[0].label)
        assertEquals(listOf("2"), groups[0].secrets.map { it.id })
        assertEquals("MFA secret", groups[1].label)
        assertEquals(listOf("1", "3"), groups[1].secrets.map { it.id })
    }

    @Test
    fun blankCategoryCollapsesIntoNoCategoryBucket() {
        val secrets =
            listOf(
                secret("1", category = "Personal"),
                secret("2", category = null),
                secret("3", category = "  "),
            )
        val groups = SecretGrouper.group(secrets, SecretGroupingMode.CATEGORY)
        assertEquals(2, groups.size)
        assertEquals("No category", groups[0].label)
        assertEquals(listOf("2", "3"), groups[0].secrets.map { it.id })
        assertEquals("Personal", groups[1].label)
        assertEquals(listOf("1"), groups[1].secrets.map { it.id })
    }

    @Test
    fun groupsPreserveEverySecret() {
        val secrets =
            listOf(
                secret("1", type = "LOGIN_PASSWORD", provider = "Google"),
                secret("2", type = "MFA_SECRET", provider = null),
                secret("3", type = "API_TOKEN", provider = "GitHub"),
            )
        val byType = SecretGrouper.group(secrets, SecretGroupingMode.TYPE)
        assertEquals(secrets.size, byType.flatMap { it.secrets }.size)
        val byProvider = SecretGrouper.group(secrets, SecretGroupingMode.PROVIDER)
        assertEquals(secrets.size, byProvider.flatMap { it.secrets }.size)
    }

    private fun secret(
        id: String,
        type: String = "LOGIN_PASSWORD",
        category: String? = null,
        provider: String? = null,
    ): SecretListItem = SecretListItem(id, "title-$id", type, category, provider, null, null)
}
