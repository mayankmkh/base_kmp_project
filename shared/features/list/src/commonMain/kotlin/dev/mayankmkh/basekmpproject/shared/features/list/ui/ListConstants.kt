package dev.mayankmkh.basekmpproject.shared.features.list.ui

object ListAnimationConfig {
    // Timing constants
    const val INITIAL_DELAY = 150
    const val ITEMS_SHOW_DELAY = 500
    const val STAGGER_DELAY = 30
    const val ITEM_STAGGER_DELAY = 50
    const val ITEM_ANIMATION_DURATION = 250
    const val ANIMATION_SELECTOR_DURATION = 200
    const val FADE_DURATION = 300
    const val SLIDE_DURATION = 500

    // Animation delays
    const val SECTION_HEADER_DELAY = 50
    const val ITEM_DELAY_MULTIPLIER = 100
    const val CHUNK_DELAY_MULTIPLIER = 10

    // Scale values
    const val PRESSED_SCALE = 0.92f
    const val BREATH_SCALE_MIN = 1f
    const val BREATH_SCALE_MAX = 1.02f
    const val AVATAR_PRESSED_SCALE = 1.15f

    // Spring animation values
    const val SCALE_DAMPING_RATIO = 0.7f
    const val SCALE_STIFFNESS = 400f
    const val AVATAR_DAMPING_RATIO = 0.6f
    const val AVATAR_STIFFNESS = 500f

    // Breathing animation
    const val BREATH_DURATION = 3000

    // Error animation
    const val ERROR_PULSE_DURATION = 600
    const val ERROR_SHAKE_DURATION = 1200
    const val ERROR_SCALE_MIN = 0.95f
    const val ERROR_SCALE_MAX = 1.08f
    const val ERROR_ROTATION_MIN = -8f
    const val ERROR_ROTATION_MAX = 8f

    // Loading animation
    const val LOADING_ROTATION_DURATION = 1200
    const val INITIAL_PULSE_MIN = 0.7f
    const val INITIAL_PULSE_MAX = 1.3f
    const val INITIAL_PULSE_DURATION = 600

    // Header floating animation
    const val HEADER_FLOAT_DURATION = 2000
    const val HEADER_FLOAT_OFFSET = -4f
}

object ListUIConfig {
    // Padding and spacing
    const val CONTENT_PADDING = 16
    const val ITEM_SPACING = 12
    const val CARD_PADDING = 16
    const val AVATAR_SIZE = 48
    const val SECTION_HEADER_PADDING = 20
    const val ANIMATION_SELECTOR_PADDING = 16
    const val ANIMATION_SELECTOR_ITEM_PADDING = 16
    const val ANIMATION_SELECTOR_VERTICAL_PADDING = 4
    const val ANIMATION_BUTTON_HORIZONTAL_PADDING = 12
    const val ANIMATION_BUTTON_VERTICAL_PADDING = 6
    const val ANIMATION_BUTTON_END_PADDING = 16
    const val SPACER_HEIGHT = 8
    const val CONTENT_SPACER_HEIGHT = 16
    const val LARGE_SPACER_HEIGHT = 24
    const val SMALL_SPACER_HEIGHT = 4
    const val SPACER_WIDTH = 16

    // Corner radius
    const val CARD_CORNER_RADIUS = 16
    const val SURFACE_CORNER_RADIUS = 20
    const val ANIMATION_SELECTOR_CORNER_RADIUS = 16
    const val ANIMATION_SELECTOR_ITEM_CORNER_RADIUS = 12

    // Elevation
    const val CARD_ELEVATION = 2
    const val ANIMATION_SELECTOR_ELEVATION = 16
    const val HEADER_ELEVATION = 4

    // Sizes
    const val LOADING_INDICATOR_SIZE = 64
    const val INITIAL_INDICATOR_SIZE = 48
    const val ERROR_ICON_SIZE = 80
    const val ERROR_ICON_INNER_SIZE = 40
    const val STROKE_WIDTH = 4

    // Z-Index values
    const val BACKGROUND_OVERLAY_Z_INDEX = 999f
    const val ANIMATION_SELECTOR_Z_INDEX = 1000f

    // Alpha values
    const val OVERLAY_ALPHA = 0.3f

    // Chunk size for items
    const val ITEMS_CHUNK_SIZE = 10

    // Text overflow
    const val TITLE_MAX_LINES = 1
    const val DESCRIPTION_MAX_LINES = 2
}
