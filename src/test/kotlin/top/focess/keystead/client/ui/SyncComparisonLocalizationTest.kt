package top.focess.keystead.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.ZhStrings

class SyncComparisonLocalizationTest {
    @Test
    fun knownSecretTypesFollowTheSelectedLanguage() {
        assertEquals("登录密码", syncComparisonTypeLabel("LOGIN_PASSWORD", ZhStrings))
        assertEquals("安全笔记", syncComparisonTypeLabel("SECURE_NOTE", ZhStrings))
        assertEquals("Login", syncComparisonTypeLabel("LOGIN_PASSWORD", EnStrings))
        assertEquals("FUTURE_TYPE", syncComparisonTypeLabel("FUTURE_TYPE", ZhStrings))
    }

    @Test
    fun tombstoneMarkersUseLocalizedRecordStates() {
        assertEquals("已删除", syncComparisonFieldValue("deleted", "true", true, ZhStrings))
        assertEquals("有效", syncComparisonFieldValue("deleted", "false", true, ZhStrings))
        assertEquals("—", syncComparisonFieldValue("deleted", "", true, ZhStrings))
    }

    @Test
    fun userValuesAndCustomFieldsRemainVerbatim() {
        assertEquals("true", syncComparisonFieldValue("password", "true", true, ZhStrings))
        assertEquals("true", syncComparisonFieldValue("deleted", "true", false, ZhStrings))
        assertEquals("My English note", syncComparisonFieldValue("body", "My English note", false, ZhStrings))
        assertEquals("  secret  ", syncComparisonFieldValue("password", "  secret  ", false, ZhStrings))
    }
}
