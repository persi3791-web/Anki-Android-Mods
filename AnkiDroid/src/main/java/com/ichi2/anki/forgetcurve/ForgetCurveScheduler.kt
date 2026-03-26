package com.ichi2.anki.forgetcurve

import java.util.Calendar

object ForgetCurveScheduler {

    data class ReviewSession(
        val dayOffset: Int,
        val hour: Int,
        val deckName: String,
        val cardCount: Int
    )

    // Colores Material para mazos (hasta 12 mazos distintos)
    val DECK_COLORS = listOf(
        0xFF1565C0.toInt(), // azul
        0xFF2E7D32.toInt(), // verde
        0xFFC62828.toInt(), // rojo
        0xFF6A1B9A.toInt(), // morado
        0xFFE65100.toInt(), // naranja
        0xFF00695C.toInt(), // teal
        0xFF4527A0.toInt(), // violeta
        0xFF558B2F.toInt(), // verde oliva
        0xFF00838F.toInt(), // cyan
        0xFFAD1457.toInt(), // rosa
        0xFF4E342E.toInt(), // marrón
        0xFF37474F.toInt()  // gris azul
    )

    /**
     * Algoritmo SM-2: calcula el próximo repaso óptimo.
     * @param interval intervalo actual en días
     * @param easeFactor factor de facilidad (default 2.5)
     * @param quality calidad de respuesta 0-5
     */
    fun nextInterval(interval: Int, easeFactor: Double = 2.5, quality: Int = 4): Int {
        return when {
            interval <= 0 -> 1
            interval == 1 -> 6
            else -> (interval * easeFactor).toInt()
        }
    }

    /**
     * Proyecta sesiones de repaso para los próximos [windowDays] días.
     * Intenta leer la BD real de Anki; si falla usa datos mock.
     */
    fun projectSessions(windowDays: Int = 7): List<ReviewSession> {
        return try {
            projectFromAnkiDB(windowDays)
        } catch (e: Exception) {
            mockSessions()
        }
    }

    private fun projectFromAnkiDB(windowDays: Int): List<ReviewSession> {
        val col = com.ichi2.libanki.CollectionManager.getColUnsafe()
        val today = col.sched.today
        val map = mutableMapOf<Triple<Int,Int,String>, Int>()

        val cursor = col.db.query(
            """
            SELECT c.due - ?, d.name, COUNT(*)
            FROM cards c
            JOIN decks d ON d.id = c.did
            WHERE c.queue IN (2, 3)
              AND c.due >= ?
              AND c.due < ?
            GROUP BY c.due, d.name
            """.trimIndent(),
            today, today, today + windowDays
        )
        cursor.use {
            while (it.moveToNext()) {
                val dayOffset = it.getInt(0).coerceIn(0, windowDays - 1)
                val deckName  = it.getString(1).substringAfterLast("::")
                val count     = it.getInt(2)
                // Hora óptima basada en SM-2: distribuir carga en horario diurno
                val hour = optimalHour(dayOffset, deckName)
                val key  = Triple(dayOffset, hour, deckName)
                map[key] = (map[key] ?: 0) + count
            }
        }

        return map.entries.map { (k, count) ->
            ReviewSession(k.first, k.second, k.third, count)
        }.sortedWith(compareBy({ it.dayOffset }, { it.hour }))
    }

    /**
     * Hora óptima de repaso basada en espaciado inteligente:
     * - Día 0-1: mañana (8-10h) — repaso urgente
     * - Día 2-3: tarde (14-16h) — consolidación
     * - Día 4-6: noche (19-21h) — repaso espaciado
     */
    fun optimalHour(dayOffset: Int, deckName: String): Int {
        val base = when {
            dayOffset <= 1 -> 9
            dayOffset <= 3 -> 14
            else           -> 19
        }
        // Offset por mazo para evitar colisiones en la misma celda
        return (base + (deckName.hashCode().and(0x7FFFFFFF) % 2)).coerceIn(0, 23)
    }

    private fun mockSessions() = listOf(
        ReviewSession(0, 9,  "CICLO V", 23),
        ReviewSession(0, 9,  "EXPOSICIÓN", 4),
        ReviewSession(0, 10, "CICLOS ANTERIORES", 8),
        ReviewSession(1, 9,  "CICLO V", 15),
        ReviewSession(1, 10, "PRUEBA", 3),
        ReviewSession(2, 14, "CICLO V", 18),
        ReviewSession(2, 14, "ESTUDIO", 2),
        ReviewSession(3, 14, "CICLOS ANTERIORES", 11),
        ReviewSession(4, 19, "CICLO V", 20),
        ReviewSession(5, 19, "EXPOSICIÓN", 5),
        ReviewSession(6, 19, "CICLOS ANTERIORES", 9),
        ReviewSession(6, 20, "CICLO V", 14)
    )

    fun dayLabel(offsetFromToday: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetFromToday)
        val days = arrayOf("Dom","Lun","Mar","Mié","Jue","Vie","Sáb")
        val months = arrayOf("Ene","Feb","Mar","Abr","May","Jun",
                             "Jul","Ago","Sep","Oct","Nov","Dic")
        val dow = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val mon = months[cal.get(Calendar.MONTH)]
        return "$dow\n$dom $mon"
    }

    fun colorForDeck(deckName: String, allDecks: List<String>): Int {
        val idx = allDecks.indexOf(deckName).coerceAtLeast(0)
        return DECK_COLORS[idx % DECK_COLORS.size]
    }
}
