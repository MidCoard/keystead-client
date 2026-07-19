package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import top.focess.keystead.model.SecretType

class SecretListFilterTest {
    private val secrets =
        listOf(
            SecretListItem(
                id = "github-login",
                title = "GitHub password",
                type = SecretType.LOGIN_PASSWORD.name,
                category = "development",
                provider = "github",
                software = "github.com",
                account = "alice",
            ),
            SecretListItem(
                id = "ssh-key",
                title = "Work SSH key",
                type = SecretType.SSH_KEY.name,
                category = "development",
                provider = "ssh",
                software = "openssh",
                account = "git",
            ),
            SecretListItem(
                id = "wechat-login",
                title = "WeChat login",
                type = SecretType.LOGIN_PASSWORD.name,
                category = "communication",
                provider = "wechat",
                software = "wechat",
                account = "alice",
            ),
        )

    @Test
    fun emptyQueryReturnsAllSecrets() {
        assertEquals(secrets, SecretListFilter.apply(secrets, SecretListQuery()))
    }

    @Test
    fun filtersByTaxonomyFieldsCaseInsensitively() {
        assertEquals(
            listOf("github-login", "ssh-key"),
            SecretListFilter.apply(secrets, SecretListQuery(category = "Development")).map { it.id },
        )
        assertEquals(
            listOf("github-login"),
            SecretListFilter.apply(secrets, SecretListQuery(software = "GITHUB.COM")).map { it.id },
        )
        assertEquals(
            listOf("wechat-login"),
            SecretListFilter.apply(secrets, SecretListQuery(provider = "wechat")).map { it.id },
        )
    }

    @Test
    fun filtersByTypeUsingExactSecretTypeName() {
        assertEquals(
            listOf("ssh-key"),
            SecretListFilter.apply(secrets, SecretListQuery(type = SecretType.SSH_KEY.name)).map { it.id },
        )
    }

    @Test
    fun freeTextSearchesIdentityAndTaxonomyFields() {
        assertEquals(
            listOf("wechat-login"),
            SecretListFilter.apply(secrets, SecretListQuery(text = "  chat  ")).map { it.id },
        )
        assertEquals(
            listOf("github-login"),
            SecretListFilter.apply(secrets, SecretListQuery(text = "alice", provider = "github")).map { it.id },
        )
        assertEquals(
            listOf("ssh-key"),
            SecretListFilter.apply(secrets, SecretListQuery(text = "ssh_key")).map { it.id },
        )
    }
}
