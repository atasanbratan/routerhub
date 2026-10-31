package dev.routerhub.app.ui.settings

import dev.routerhub.api.ChoiceOption
import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.TableCodec
import dev.routerhub.api.TableColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {
    private val table = SettingField.Table(
        key = "rules",
        title = "Rules",
        columns = listOf(TableColumn(key = "name", title = "Name")),
        rows = listOf(listOf("first")),
        maxRows = 2,
        itemName = "rule",
    )
    private val secret = SettingField.Secret("secret", "Password", minLength = 8, maxLength = 12, requiresConfirmation = true)
    private val page = SettingsPage(
        SettingsPageInfo("page", "Page", "Test"),
        listOf(
            SettingField.Info("info", "Info", "shown"),
            SettingField.Text("text", "Text", "old", maxLength = 5),
            SettingField.Toggle("toggle", "Toggle", true),
            SettingField.Number("number", "Number", 10, min = 1, max = 100),
            SettingField.Choice("choice", "Choice", "a", listOf(ChoiceOption("a", "A"), ChoiceOption("b", "B"))),
            secret,
            table,
        ),
    )

    @Test
    fun `untouched fields are not sent`() {
        assertEquals(emptyMap<String, String>(), pendingChanges(page, emptyMap()))
    }

    @Test
    fun `values put back to what the router has are not sent`() {
        val edits = mapOf(
            "text" to "old",
            "toggle" to "true",
            "number" to " 10 ",
            "choice" to "a",
            "rules" to TableCodec.encode(table.rows),
        )
        assertEquals(emptyMap<String, String>(), pendingChanges(page, edits))
    }

    @Test
    fun `only the changed values are sent`() {
        assertEquals(
            mapOf("text" to "new", "number" to "11"),
            pendingChanges(page, mapOf("text" to "new", "toggle" to "true", "number" to "11 ")),
        )
    }

    @Test
    fun `a blank secret keeps the current one and an info field is never sent`() {
        assertEquals(emptyMap<String, String>(), pendingChanges(page, mapOf("secret" to "", "info" to "edited")))
        assertEquals(mapOf("secret" to "new-value"), pendingChanges(page, mapOf("secret" to "new-value")))
    }

    @Test
    fun `a secret that must be typed twice needs a matching second entry`() {
        val changes = mapOf("secret" to "new-value")
        assertTrue(unconfirmed(page, changes, emptyMap()))
        assertTrue(unconfirmed(page, changes, mapOf("secret" to "something-else")))
        assertFalse(unconfirmed(page, changes, mapOf("secret" to "new-value")))
        assertFalse(unconfirmed(page, emptyMap(), emptyMap()))
    }

    @Test
    fun `numbers must be inside the field's range`() {
        val field = page.fields[3]
        assertNull(localError(field, "100"))
        assertEquals(LocalError.OutOfRange(1, 100), localError(field, "101"))
        assertEquals(LocalError.OutOfRange(1, 100), localError(field, ""))
    }

    @Test
    fun `texts and secrets keep to their lengths`() {
        assertNull(localError(page.fields[1], "12345"))
        assertEquals(LocalError.TooLong, localError(page.fields[1], "123456"))
        assertNull(localError(secret, ""))
        assertEquals(LocalError.TooShort(8), localError(secret, "short"))
        assertEquals(LocalError.OverLength(12), localError(secret, "much-too-long-value"))
        assertNull(localError(secret, "just-right"))
    }

    @Test
    fun `a table keeps to its row limit`() {
        val rows = { n: Int -> TableCodec.encode(List(n) { listOf("row $it") }) }
        assertNull(localError(table, rows(2)))
        assertEquals(LocalError.TooManyRows(2, "rule"), localError(table, rows(3)))
    }
}
