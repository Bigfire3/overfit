package app.overfit.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun routesAreUnique() {
        val routes = TopLevelDestination.entries.map { it.routeClass }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun startIsFirstTab() {
        assertEquals(StartRoute, TopLevelDestination.entries.first().route)
    }
}
