// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

import java.security.SecureRandom

fun interface PocketRng { fun nextIndex(bound: Int): Int }

/** CSPRNG-backed; [SecureRandom.nextInt] is uniform (no modulo bias). The outcome is drawn BEFORE the animation. */
class SecureRng(private val random: SecureRandom = SecureRandom()) : PocketRng {
    override fun nextIndex(bound: Int): Int = random.nextInt(bound)
}
