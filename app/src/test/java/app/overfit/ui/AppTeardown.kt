package app.overfit.ui

import app.overfit.data.AppData
import app.overfit.data.db.OverfitDatabase
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource

/**
 * Closes the Room database of a Compose UI test only after nothing can still use it.
 *
 * `@After` methods run *inside* the Compose rule, while the composition is still alive and keeps collecting
 * Room flows; a `database.close()` there races with those queries ("connection pool has been closed").
 * Declare this rule with a lower `order` than the Compose rule, so it finishes after the rule has disposed the
 * composition (and with it every composition-bound coroutine):
 *
 * ```
 * @get:Rule(order = 0) val teardown = AppTeardown({ data }, { database })
 * @get:Rule(order = 1) val compose = createComposeRule()
 * ```
 *
 * Then the app-wide scope of [AppData] (background work that outlives screens) is cancelled, and last the database closed.
 */
class AppTeardown(
    private val data: () -> AppData?,
    private val database: () -> OverfitDatabase,
    /** For tests that create the app only in some of their cases: nothing to close when this says no. */
    private val closeDatabase: () -> Boolean = { true },
) : ExternalResource() {
    override fun after() {
        if (!closeDatabase()) return
        data()?.scope?.cancel()
        database().close()
    }
}
