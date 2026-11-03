package dev.routerhub.app.extension

import dev.routerhub.api.ChoiceOption
import dev.routerhub.api.ClientAction
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.SettingsPageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class ExtensionTextTest {
    private val owner = ExtensionTextTest::class.java
    private val portuguese = ExtensionText.load(owner, Locale.forLanguageTag("pt-PT"))

    @Test
    fun `titles, labels and warnings are translated, keys and values are not`() {
        val page = SettingsPage(
            SettingsPageInfo("password", "Router password", "system"),
            listOf(
                SettingField.Secret("password", "Router password"),
                SettingField.Choice("mode", "Mode", "block", listOf(ChoiceOption("block", "Block"))),
            ),
            applyWarning = "Saving restarts Wi-Fi",
        )
        val translated = portuguese.page(page)
        assertEquals("Senha do roteador", translated.info.title)
        assertEquals("password", translated.info.id)
        assertEquals("system", translated.info.group)
        assertEquals("Salvar reinicia o Wi-Fi", translated.applyWarning)
        val choice = translated.fields[1] as SettingField.Choice
        assertEquals("Modo", choice.title)
        assertEquals("mode", choice.key)
        assertEquals("block", choice.value)
        assertEquals(ChoiceOption("block", "Bloquear"), choice.options.single())
    }

    @Test
    fun `text without a translation stays in English`() {
        assertEquals("Port forwarding", portuguese.tr("Port forwarding"))
        assertEquals("Allow", portuguese.action(ClientAction("allow", "Allow")).title)
        // An empty translation counts as none.
        assertEquals("Rule", portuguese.tr("Rule"))
    }

    @Test
    fun `status labels are translated, their values are not`() {
        val status = portuguese.status(RouterStatus(extras = mapOf("Free memory" to "12 MB")))
        assertEquals(mapOf("Memória livre" to "12 MB"), status.extras)
    }

    @Test
    fun `a region's own file wins, and the language's fills the rest`() {
        val brazil = ExtensionText.load(owner, Locale.forLanguageTag("pt-BR"))
        assertEquals("Senha do roteador (Brasil)", brazil.tr("Router password"))
        assertEquals("Modo", brazil.tr("Mode"))
    }

    @Test
    fun `English, missing and broken files leave the text as it is`() {
        assertSame(ExtensionText.NONE, ExtensionText.load(owner, Locale.ENGLISH))
        assertSame(ExtensionText.NONE, ExtensionText.load(owner, Locale.forLanguageTag("es")))
        assertSame(ExtensionText.NONE, ExtensionText.load(owner, Locale.GERMAN))
    }
}
