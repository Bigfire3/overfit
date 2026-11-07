package app.overfit.ui.muscles

import androidx.compose.ui.geometry.Offset
import app.overfit.domain.Muscle
import app.overfit.domain.MuscleMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyShapesTest {
    @Test fun everyShownMuscleHasARegion() {
        val drawn = (BodyShapes.front + BodyShapes.back).mapNotNull { it.muscle }.toSet()
        assertEquals(MuscleMap.shown.toSet(), drawn)
    }

    @Test fun tapOnARegionCentreHitsThatMuscle() {
        listOf(BodyShapes.front, BodyShapes.back).forEach { shapes ->
            shapes.filter { it.muscle != null }.forEach { shape ->
                assertEquals(shape.muscle, BodyShapes.hit(shapes, shape.center), "${shape.muscle} at ${shape.center}")
            }
        }
    }

    @Test fun headAndEmptySpaceHitNothingButANearMissOnASmallRegionCounts() {
        assertNull(BodyShapes.hit(BodyShapes.front, Offset(50f, 14f))) // head
        assertNull(BodyShapes.hit(BodyShapes.front, Offset(5f, 100f))) // beside the figure
        // Just outside the back adductor sliver (x 48–49.4) still selects it.
        assertEquals(Muscle.ADDUCTORS, BodyShapes.hit(BodyShapes.back, Offset(47.2f, 108f)))
    }

    @Test fun figuresAreMirrored() {
        BodyShapes.front.filter { it.oval == null && it.muscle != null }.forEach { s ->
            assertTrue(s.points.all { it.x in 15f..85f && it.y in 0f..180f })
        }
    }
}
