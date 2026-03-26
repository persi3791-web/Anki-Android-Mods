package com.ichi2.anki.forgetcurve

import java.util.Calendar

/**
 * Implementación simplificada del algoritmo SM-2.
 * Calcula las sesiones de repaso proyectadas para los próximos 7 días.
 */
object ForgetCurveScheduler {

    data class ReviewSession(
        val dayOffset: Int,   // 0 = hoy, 1 = mañana, etc.
        val hour: Int,        // hora sugerida (0–23)
        val cardCount: Int
    )

    /**
     * Dado un conjunto de tarjetas con su intervalo actual (días),
     * devuelve qué sesiones caen dentro de los próximos [windowDays] días.
     *
     * @param cardIntervals lista de pares (intervaloDías, horaPreferida)
     * @param windowDays    ventana de proyección (default 7)
     */
    fun projectSessions(
        cardIntervals: List<Pair<Int, Int>>,
        windowDays: Int = 7
    ): List<ReviewSession> {
        val map = mutableMapOf<Pair<Int,Int>, Int>() // (día, hora) → count

        for ((interval, preferredHour) in cardIntervals) {
            if (interval in 0 until windowDays) {
                val key = Pair(interval, preferredHour.coerceIn(0, 23))
                map[key] = (map[key] ?: 0) + 1
            }
        }

        return map.entries
            .map { (k, count) -> ReviewSession(k.first, k.second, count) }
            .sortedWith(compareBy({ it.dayOffset }, { it.hour }))
    }

    /** Etiqueta corta para mostrar en el eje X del calendario. */
    fun dayLabel(offsetFromToday: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetFromToday)
        val days = arrayOf("Dom","Lun","Mar","Mié","Jue","Vie","Sáb")
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
}
