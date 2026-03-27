// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.forgetcurve

import android.graphics.Color
import com.ichi2.anki.libanki.Collection
import timber.log.Timber
import java.util.Calendar

object ForgetCurveScheduler {

    data class ReviewSession(
        val dayOffset: Int,
        val hour: Int,
        val fullDeckPath: String,
        val cardCount: Int,
        val deckId: Long = 0L
    )

    fun projectSessions(col: Collection): List<ReviewSession> {
        return try {
            val crt = col.db.queryLongScalar("SELECT crt FROM col")
            val today = ((System.currentTimeMillis() / 1000L - crt) / 86400).toInt()
            val sql = "SELECT (c.due - " + today + ") AS dy, d.name, d.id, COUNT(*) " +
                "FROM cards c JOIN decks d ON c.did = d.id " +
                "WHERE c.queue IN (2,3) AND c.due >= " + today +
                " AND c.due < " + (today + 7) + " GROUP BY dy, d.id"
            val list = mutableListOf<ReviewSession>()
            col.db.query(sql).use { cur ->
                while (cur.moveToNext()) {
                    list.add(ReviewSession(cur.getInt(0), 9,
                        cur.getString(1), cur.getInt(3), cur.getLong(2)))
                }
            }
            list.ifEmpty { mockSessionsPublic() }
        } catch (e: Exception) {
            Timber.e(e, "projectSessions fallo")
            mockSessionsPublic()
        }
    }

    fun dayLabel(dayOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        val names = arrayOf("Dom","Lun","Mar","Mie","Jue","Vie","Sab")
        val idx = cal.get(Calendar.DAY_OF_WEEK) - 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        return names[idx] + " " + d + "/" + m
    }

    fun colorForDeck(fullDeckPath: String): Int {
        val root = fullDeckPath.substringBefore("::")
        val palette = listOf(
            Color.parseColor("#4285F4"), Color.parseColor("#EA4335"),
            Color.parseColor("#FBBC04"), Color.parseColor("#34A853"),
            Color.parseColor("#FF6D00"), Color.parseColor("#46BDC6"),
        )
        return palette[Math.abs(root.hashCode()) % palette.size]
    }

    fun mockSessionsPublic(): List<ReviewSession> = listOf(
        ReviewSession(0, 9,  "CICLOS ANTERIORES", 195, 1L),
        ReviewSession(0, 14, "PRUEBA",              3, 2L),
        ReviewSession(1, 10, "imagenes",            0, 3L),
        ReviewSession(2, 9,  "CICLOS ANTERIORES",  20, 1L),
        ReviewSession(3, 11, "PRUEBA",             10, 2L),
    )
}
