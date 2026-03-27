// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.forgetcurve

import android.graphics.Color
import com.ichi2.libanki.Collection
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
            val today = col.sched.today
            val sql = """
                SELECT
                    (c.due - $today) AS dayOffset,
                    d.name AS deckName,
                    COUNT(*) AS cardCount
                FROM cards c
                JOIN decks d ON c.did = d.id
                WHERE c.queue IN (2, 3)
                    AND c.due >= $today
                    AND c.due < ${today + 7}
                GROUP BY dayOffset, d.name
            """.trimIndent()

            val sessions = mutableListOf<ReviewSession>()
            col.db.rawQuery(sql).use { cursor ->
                while (cursor.moveToNext()) {
                    val dayOffset = cursor.getInt(0)
                    val deckName = cursor.getString(1)
                    val count = cursor.getInt(2)
                    sessions.add(ReviewSession(dayOffset, 8, deckName, count))
                }
            }
            sessions
        } catch (e: Exception) {
            Timber.e(e, "projectSessions falló, usando mock")
            mockSessionsPublic()
        }
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
        ReviewSession(0, 9,  "Matemáticas::Álgebra",   15),
        ReviewSession(0, 14, "Historia::Antigua",        8),
        ReviewSession(1, 10, "Inglés::Vocabulario",     20),
        ReviewSession(2, 9,  "Matemáticas::Cálculo",   12),
        ReviewSession(3, 11, "Historia::Medieval",      10),
    )
}
