package com.jambus.heji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeRowGesturePolicyTest {
  @Test
  fun `touch slop delays axis selection`() {
    assertEquals(
      SwipeRowGesturePolicy.Axis.UNDECIDED,
      SwipeRowGesturePolicy.axisAfterSlop(7f, 6f, 8f)
    )
  }

  @Test
  fun `horizontal axis needs one point two five times vertical travel`() {
    assertEquals(
      SwipeRowGesturePolicy.Axis.HORIZONTAL,
      SwipeRowGesturePolicy.axisAfterSlop(11f, 8f, 8f)
    )
    assertEquals(
      SwipeRowGesturePolicy.Axis.VERTICAL,
      SwipeRowGesturePolicy.axisAfterSlop(10f, 9f, 8f)
    )
  }

  @Test
  fun `translation is clamped to the action strip`() {
    assertEquals(-152f, SwipeRowGesturePolicy.clampTranslation(-300f, 152))
    assertEquals(-20f, SwipeRowGesturePolicy.clampTranslation(-20f, 152))
    assertEquals(0f, SwipeRowGesturePolicy.clampTranslation(12f, 152))
  }

  @Test
  fun `forty four percent closes and forty five percent opens`() {
    assertFalse(SwipeRowGesturePolicy.shouldOpen(-44f, 0f, 300f, 100))
    assertTrue(SwipeRowGesturePolicy.shouldOpen(-45f, 0f, 300f, 100))
  }

  @Test
  fun `fling direction overrides distance`() {
    assertTrue(SwipeRowGesturePolicy.shouldOpen(-5f, -500f, 300f, 100))
    assertFalse(SwipeRowGesturePolicy.shouldOpen(-95f, 500f, 300f, 100))
  }

  @Test
  fun `disabled animation settles immediately`() {
    assertEquals(SwipeRowGesturePolicy.ANIMATION_DURATION_MS, SwipeRowGesturePolicy.animationDuration(true))
    assertEquals(0L, SwipeRowGesturePolicy.animationDuration(false))
  }

  @Test
  fun `vertical release is ignored rather than activating a row`() {
    assertEquals(
      SwipeRowGesturePolicy.ReleaseAction.IGNORE,
      SwipeRowGesturePolicy.releaseAction(SwipeRowGesturePolicy.Axis.VERTICAL)
    )
  }

  @Test
  fun `outside dismiss suppresses only the first short row tap`() {
    val down = SwipeDismissTouchPolicy.begin(100f, 200f)

    assertTrue(SwipeDismissTouchPolicy.suppressActivation(down))
    assertTrue(SwipeDismissTouchPolicy.shouldCancelTargetOnFinish(down))
    val drag = SwipeDismissTouchPolicy.onMove(down, 100f, 240f, 8f)
    assertFalse(SwipeDismissTouchPolicy.suppressActivation(drag))
    assertFalse(SwipeDismissTouchPolicy.shouldCancelTargetOnFinish(drag))
  }

  @Test
  fun `velocity samples preserve raw screen coordinates`() {
    assertEquals(
      SwipeRowGesturePolicy.VelocitySample(320f, 480f),
      SwipeRowGesturePolicy.velocitySample(320f, 480f)
    )
  }
}
