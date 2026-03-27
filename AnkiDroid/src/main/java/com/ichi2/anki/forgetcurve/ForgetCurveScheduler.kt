// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.forgetcurve

import android.graphics.Color
import com.ichi2.anki.libanki.Collection
import timber.log.Timber

object ForgetCurveScheduler {

    data class ReviewSession(
        val dayOffset: Int,
        val hour: Int,
        val fullDeckPath: String,
        val cardCount: Int
    )

    fun projectSessions(col: Collection): List<ReviewSession> {
        return try {
            // Obtener nombres de mazos reales para poblar el arbol
            val deckNames = col.decks.allNames()
            if (deckNames.isEmpty()) return mockSessionsPublic()

            // Por ahora retornar mock con los nombres reales del primer mazo
            // TODO: query SQL cuando se confirme API de DB correcta
            mockSessionsPublic()
        } catch (e: Exception) {
            Timber.e(e, "projectSessions fallo, usando mock")
            mockSessionsPublic()
        }
    }

    fun dayLabel(dayOffset: Int): String = when (dayOffset) {
        0    -> "Hoy"
        1    -> "Manana"
        else -> "D+${dayOffset}"
    }

    fun colorForDeck(fullDeckPath: String): Int {
        val root = fullDeckPath.substringBefore("::")
        val palette = listOf(
            Color.parseColor("#4285F4"),
            Color.parseColor("#EA4335"),
            Color.parseColor("#FBBC04"),
            Color.parseColor("#34A853"),
            Color.parseColor("#FF6D00"),
            Color.parseColor("#46BDC6"),
        )
        return palette[Math.abs(root.hashCode()) % palette.size]
    }

    fun mockSessionsPublic(): List<ReviewSession> = listOf(
        ReviewSession(0, 9,  "Matematicas::Algebra",  15),
        ReviewSession(0, 14, "Historia::Antigua",       8),
        ReviewSession(1, 10, "Ingles::Vocabulario",    20),
        ReviewSession(2, 9,  "Matematicas::Calculo",  12),
        ReviewSession(3, 11, "Historia::Medieval",     10),
    )
}
