package dev.routerhub.app.ui.settings

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.routerhub.api.ChoiceOption
import dev.routerhub.api.InvalidSettingException
import dev.routerhub.api.SettingField
import dev.routerhub.api.SettingsPage
import dev.routerhub.api.SettingsPageInfo
import dev.routerhub.api.TableCodec
import dev.routerhub.api.TableColumn
import dev.routerhub.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The settings form with its real view model, against a fake router. Run with connectedGithubDebugAndroidTest. */
class SettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private class FakeRouter(private val page: SettingsPage) : SettingsSource {
        val writes = mutableListOf<Map<String, String>>()
        var refusal: InvalidSettingException? = null

        override suspend fun read() = page

        override suspend fun write(changes: Map<String, String>) {
            refusal?.let { throw it }
            writes += changes
        }

        override fun loginPasswordChanged(password: String) = Unit

        override fun changed() = Unit
    }

    private fun show(vararg fields: SettingField): FakeRouter {
        val router = FakeRouter(SettingsPage(SettingsPageInfo("page", "Test page", "Test"), fields.toList()))
        val viewModel = SettingsPageViewModel(router, context)
        compose.setContent { MaterialTheme { SettingsPageContent(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodesWithText("Test page").fetchSemanticsNodes().isNotEmpty() }
        return router
    }

    private val save get() = compose.onNodeWithText(context.getString(R.string.save))

    private fun type(label: String, text: String) =
        compose.onNode(hasSetTextAction() and hasText(label)).performTextReplacement(text)

    private fun saved(router: FakeRouter): Map<String, String> {
        compose.waitUntil { router.writes.isNotEmpty() }
        return router.writes.single()
    }

    @Test
    fun editingOneFieldSendsOnlyThatField() {
        val router = show(
            SettingField.Text("ssid", "Network name", "old"),
            SettingField.Text("other", "Other value", "same"),
            SettingField.Toggle("radio", "Radio", true),
        )
        type("Network name", "new")
        save.performClick()
        assertEquals(mapOf("ssid" to "new"), saved(router))
    }

    @Test
    fun saveIsDisabledWhenNothingChanged() {
        show(SettingField.Text("ssid", "Network name", "old"))
        save.assertIsNotEnabled()
        type("Network name", "new")
        save.assertIsEnabled()
        type("Network name", "old")
        save.assertIsNotEnabled()
    }

    @Test
    fun theTwoEntriesOfASecretMustMatchBeforeSaving() {
        val router = show(SettingField.Secret("key", "Passphrase", requiresConfirmation = true))
        type("Passphrase", "a-new-value")
        save.assertIsNotEnabled()

        type("Confirm passphrase", "something-else")
        compose.onNodeWithText(context.getString(R.string.entries_differ)).assertIsDisplayed()
        save.assertIsNotEnabled()

        type("Confirm passphrase", "a-new-value")
        save.assertIsEnabled().performClick()
        assertEquals(mapOf("key" to "a-new-value"), saved(router))
    }

    @Test
    fun addingARowSubmitsTheCompleteList() {
        val router = show(rules(listOf("first")))
        compose.onNodeWithText(context.getString(R.string.add)).performClick()
        type("Name", "second")
        compose.onAllNodesWithText(context.getString(R.string.add)).onLast().performClick()
        save.performClick()
        assertEquals(listOf(listOf("first"), listOf("second")), TableCodec.decode(saved(router).getValue("rules")))
    }

    @Test
    fun removingARowSubmitsTheCompleteList() {
        val router = show(rules(listOf("first"), listOf("second")))
        compose.onAllNodes(hasContentDescription(context.getString(R.string.table_remove, "rule"))).onFirst().performClick()
        save.performClick()
        assertEquals(listOf(listOf("second")), TableCodec.decode(saved(router).getValue("rules")))
    }

    @Test
    fun aRefusalShowsUnderAToggle() {
        val router = show(SettingField.Toggle("radio", "Radio", true))
        router.refusal = InvalidSettingException("radio", "The radio can't be turned off now")
        compose.onNodeWithText("Radio").performClick()
        save.performClick()
        compose.waitUntil { compose.onAllNodesWithText("The radio can't be turned off now").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aRefusalShowsUnderAChoice() {
        val router = show(SettingField.Choice("mode", "Mode", "a", listOf(ChoiceOption("a", "Auto"), ChoiceOption("b", "Fixed"))))
        router.refusal = InvalidSettingException("mode", "Not on this band")
        compose.onNodeWithText("Mode").performClick()
        compose.onNodeWithText("Fixed").performClick()
        save.performClick()
        compose.waitUntil { compose.onAllNodesWithText("Not on this band").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun rules(vararg rows: List<String>) = SettingField.Table(
        key = "rules",
        title = "Rules",
        columns = listOf(TableColumn(key = "name", title = "Name")),
        rows = rows.toList(),
        itemName = "rule",
    )
}
