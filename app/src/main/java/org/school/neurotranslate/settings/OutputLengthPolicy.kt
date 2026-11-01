package org.school.neurotranslate.settings

import kotlin.math.roundToInt

fun normalizeMaxOutputLength(value: Float): Int =
    (value / STEP).roundToInt().times(STEP).coerceIn(MIN_LENGTH, MAX_LENGTH)

private const val STEP = 16
private const val MIN_LENGTH = 64
private const val MAX_LENGTH = 400
