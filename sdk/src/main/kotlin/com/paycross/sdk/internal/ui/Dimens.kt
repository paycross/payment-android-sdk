package com.paycross.sdk.internal.ui

import androidx.compose.ui.unit.dp

/**
 * Material's minimum touch target, and the floor the sheet holds its controls to.
 *
 * Set explicitly wherever a control would otherwise measure less: the save-card
 * row, which is a caption beside a checkbox, and the card-removal button, whose
 * Material default is 40dp and whose interactive-size enforcement does not reach
 * the bounds an accessibility service reads. Everything else — the text fields,
 * the Pay button, the selectable card rows — already clears it.
 */
internal val MIN_TOUCH_TARGET = 48.dp
