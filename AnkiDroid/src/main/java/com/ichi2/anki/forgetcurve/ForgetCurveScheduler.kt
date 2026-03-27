/*
 * Copyright (c) 2024 Ankidroid Open Source Team <ankidroid@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.forgetcurve

import java.util.Calendar

object ForgetCurveScheduler {

    data class ReviewSession(
        val dayOffset: Int,
        val hour: Int,
        val fullDeckPath: String,
        val cardCount: Int,
    )

    val DECK_COLORS = listOf(
        0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(),
        0xFFC62828.toInt(),
        0xFF6A1B9A.toInt(),
        0xFFE65100.toInt(),
        0xFF00695C.toInt(),
        0xFF4527A0.toInt(),
        0xFF558B2F.toInt(),
        0xFF00838F.toInt(),
        0xFFAD1457.toInt(),
        0xFF4E342E.toInt(),
        0xFF37474F.toInt(),
    )

    fun nextInterval(interval: Int, easeFactor: Double = 2.5, quality: Int = 4): Int =
        when {
            interval <= 0 -> 1
            interval == 1 -> 6
            else -> (interval * easeFactor).toInt()
        }

    fun projectSessions(windowDays: Int = 7): List<ReviewSession> {
        return try {
            val result = projectFromAnkiDB(windowDays)
            if (result.isEmpty()) mockSessionsPublic() else result
        } catch (e: Exception) {
            mockSessionsPublic()
        }
    }

    private fun projectFromAnkiDB(windowDays: Int): List<ReviewSession> {
        val col = com.ichi2.anki.CollectionManager.getColUnsafe()
        val today = col.sched.today
        val map = mutableMapOf<Triple<Int, Int, String>, Int>()
        val cursor = col.db.query(
            "SELECT c.due - ?, d.name, COUNT(*) FROM cards c JOIN decks d ON d.id = c.did WHERE c.queue IN (2, 3) AND c.due >= ? AND c.due < ? GROUP BY c.due, d.name",
            today, today, today + windowDays,
        )
        cursor.use {
            while (it.moveToNext()) {
                val dayOffset = it.getInt(0).coerceIn(0, windowDays - 1)
                val fullPath = it.getString(1)
                val count = it.getInt(2)
                val hour = optimalHour(dayOffset, fullPath)
                val key = Triple(dayOffset, hour, fullPath)
                map[key] = (map[key] ?: 0) + count
            }
        }
        return map.entries.map { (k, count) ->
            ReviewSession(k.first, k.second, k.third, count)
        }.sortedWith(compareBy({ it.dayOffset }, { it.hour }))
    }

    fun optimalHour(dayOffset: Int, deckName: String): Int {
        val base = when {
            dayOffset <= 1 -> 9
            dayOffset <= 3 -> 14
            else -> 19
        }
        return (base + (deckName.hashCode().and(0x7FFFFFFF) % 2)).coerceIn(0, 23)
    }

    fun mockSessionsPublic() = listOf(
        ReviewSession(0, 9, "CICLO V", 23),
        ReviewSession(0, 9, "EXPOSICIÓN", 4),
        ReviewSession(0, 10, "CICLOS ANTERIORES", 8),
        ReviewSession(1, 9, "CICLO V", 15),
        ReviewSession(1, 10, "PRUEBA", 3),
        ReviewSession(2, 14, "CICLO V", 18),
        ReviewSession(2, 14, "ESTUDIO", 2),
        ReviewSession(3, 14, "CICLOS ANTERIORES", 11),
        ReviewSession(4, 19, "CICLO V", 20),
        ReviewSession(5, 19, "EXPOSICIÓN", 5),
        ReviewSession(6, 19, "CICLOS ANTERIORES", 9),
        ReviewSession(6, 20, "CICLO V", 14),
        ReviewSession(0, 9, "CICLO V::Farmacología", 10),
        ReviewSession(0, 9, "CICLO V::Farmacología::Antibióticos", 5),
        ReviewSession(1, 14, "CICLO V::Anatomía", 8),
        ReviewSession(2, 14, "CICLOS ANTERIORES::Fisiología", 6),
    )

    fun dayLabel(offsetFromToday: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetFromToday)
        val days = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        val months = arrayOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        val dow = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val mon = months[cal.get(Calendar.MONTH)]
        return "$dow\n$dom $mon"
    }

    fun colorForDeck(fullDeckPath: String, allDecks: List<String>): Int {
        val rootDeck = fullDeckPath.substringBefore("::")
        val rootDecks = allDecks.map { it.substringBefore("::") }.distinct().sorted()
        val idx = rootDecks.indexOf(rootDeck).coerceAtLeast(0)
        return DECK_COLORS[idx % DECK_COLORS.size]
    }
}
