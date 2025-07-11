# Quick Reference Guide

## File Locations

| File | Purpose | Key Exports |
|------|---------|-------------|
| `ListContent.kt` | Main orchestrator | `ListContent`, `ListUIState` |
| `ListAnimations.kt` | Animation system | `AnimationType`, `AnimationStrategy`, `AnimationValue` |
| `ListAnimationHooks.kt` | Custom hooks | `useItemAnimation`, `useBreathingEffect`, etc. |
| `ListConstants.kt` | Configuration | `ListAnimationConfig`, `ListUIConfig` |
| `ListComponents.kt` | UI components | `ItemCard`, `StickyHeader`, `AnimationSelector` |
| `ListStates.kt` | State composables | `InitialState`, `SuccessState`, etc. |

## Common Tasks

### 1. Change Animation Timing

```kotlin
// In ListConstants.kt
object ListAnimationConfig {
    const val ITEM_ANIMATION_DURATION = 300 // Change from 250
}
```

### 2. Add New Animation Type

```kotlin
// 1. Add to enum in ListAnimations.kt
enum class AnimationType(val displayName: String) {
    // ... existing
    NEW_ANIMATION("New Animation")
}

// 2. Create strategy
class NewAnimationStrategy : AnimationStrategy {
    @Composable
    override fun animate(trigger: Int, itemIndex: Int, isActive: Boolean): AnimationValue {
        // Implementation
    }
}

// 3. Add to factory
AnimationType.NEW_ANIMATION -> NewAnimationStrategy()
```

### 3. Modify UI Spacing

```kotlin
// In ListConstants.kt
object ListUIConfig {
    const val ITEM_SPACING = 16 // Change from 12
    const val CARD_PADDING = 20 // Change from 16
}
```

### 4. Create Custom Hook

```kotlin
// In ListAnimationHooks.kt
@Composable
fun useCustomEffect(
    trigger: Int,
    duration: Int = ListAnimationConfig.CUSTOM_DURATION
): Float {
    return animateFloatAsState(
        targetValue = if (trigger > 0) 1f else 0f,
        animationSpec = tween(duration),
        label = "custom"
    ).value
}
```

### 5. Add New UI Component

```kotlin
// In ListComponents.kt
@Composable
fun NewComponent(
    modifier: Modifier = Modifier,
    // ... parameters
) {
    // Implementation using existing hooks and constants
}
```

## Animation Types Available

| Type | Effect | Strategy Class |
|------|--------|----------------|
| `FLIP` | 3D rotation | `FlipAnimationStrategy` |
| `BOUNCE` | Scale bounce | `BounceAnimationStrategy` |
| `SPIN` | Z-axis rotation | `SpinAnimationStrategy` |
| `ZOOM` | Scale in/out | `ZoomAnimationStrategy` |
| `SLIDE` | Horizontal slide | `SlideAnimationStrategy` |
| `FADE` | Alpha transition | `FadeAnimationStrategy` |
| `WAVE` | Combined movement | `WaveAnimationStrategy` |
| `PULSE` | Scale pulse | `PulseAnimationStrategy` |

## Available Hooks

| Hook | Purpose | Returns |
|------|---------|---------|
| `useStaggeredVisibility` | Show items with delay | `Boolean` |
| `useAnimationTrigger` | Trigger animations | `Int` |
| `useItemAnimation` | Item-specific animation | `AnimationValue` |
| `useBreathingEffect` | Subtle scale animation | `Float` |
| `usePressedScale` | Press feedback | `Float` |
| `usePulseAnimation` | Pulsing effect | `Float` |
| `useRotationAnimation` | Continuous rotation | `Float` |
| `useFloatingAnimation` | Floating movement | `Float` |
| `useErrorAnimation` | Error state animations | `Pair<Float, Float>` |

## State Management

```kotlin
data class ListUIState(
    val showItems: Boolean = false,
    val animationTrigger: Int = 0,
    val selectedAnimation: AnimationType = AnimationType.FLIP,
    val showAnimationSelector: Boolean = false
)

// Update state
listUIState = listUIState.copy(
    selectedAnimation = newAnimation,
    animationTrigger = listUIState.animationTrigger + 1
)
```

## Testing Patterns

### Test Component
```kotlin
@Test
fun testComponent() {
    composeTestRule.setContent {
        ComponentName(
            // ... props
        )
    }
    // Assertions
}
```

### Test Hook
```kotlin
@Test
fun testHook() {
    // Test hook behavior with different inputs
    val result = useCustomHook(trigger = 1)
    assertEquals(expected, result)
}
```

### Test Strategy
```kotlin
@Test
fun testStrategy() {
    val strategy = AnimationStrategy()
    val result = strategy.animate(1, 0, true)
    // Assert result properties
}
```

## Performance Tips

1. **Use `remember()` for expensive calculations**
2. **Avoid unnecessary recompositions**
3. **Use `LaunchedEffect` for side effects**
4. **Stagger animations to avoid overwhelming the system**
5. **Memoize animation strategies**

## Common Issues & Solutions

### Issue: Animation not triggering
**Solution**: Check if `animationTrigger` is being incremented

### Issue: Component not updating
**Solution**: Ensure state is being updated with `copy()`

### Issue: Animation too fast/slow
**Solution**: Adjust timing constants in `ListAnimationConfig`

### Issue: Layout issues
**Solution**: Check spacing constants in `ListUIConfig`

## Best Practices

1. **Always use constants from config files**
2. **Create focused, single-responsibility components**
3. **Use custom hooks for reusable logic**
4. **Test individual components in isolation**
5. **Keep animation logic separate from UI logic**
6. **Use meaningful names for animation labels**
7. **Document complex animation behaviors**

## Migration Guide (From Old Code)

### Old Pattern → New Pattern

```kotlin
// OLD: Inline animation logic
val scale by animateFloatAsState(targetValue = 1f)

// NEW: Use custom hook
val scale = usePressedScale(isPressed = false)
```

```kotlin
// OLD: Magic numbers
delay(150)
tween(300)

// NEW: Use constants
delay(ListAnimationConfig.INITIAL_DELAY.toLong())
tween(ListAnimationConfig.FADE_DURATION)
```

```kotlin
// OLD: Large when statement
when (selectedAnimation) {
    AnimationType.FLIP -> { /* inline logic */ }
    // ...
}

// NEW: Use strategy pattern
val animationValue = useItemAnimation(trigger, itemIndex, selectedAnimation)
``` 