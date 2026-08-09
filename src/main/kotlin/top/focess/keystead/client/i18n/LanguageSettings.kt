package top.focess.keystead.client.i18n

import top.focess.keystead.client.ClientSettingsStore

/**
 * Persists the chosen [AppLocale] in the consolidated client settings (settings.json) so the
 * language survives restarts.
 */
internal class LanguageSettings(private val store: ClientSettingsStore) {
    fun load(): AppLocale? {
        val tag = store.load().locale?.takeIf(String::isNotBlank) ?: return null
        return runCatching { AppLocale.forLanguageTag(tag) }.getOrNull()
    }

    fun save(locale: AppLocale) {
        val settings = store.load()
        settings.locale = locale.languageTag
        store.save(settings)
    }
}
