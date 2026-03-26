package com.ichi2.anki.forgetcurve

import android.os.Bundle
import android.view.*
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ichi2.anki.R

/**
 * Fragment que muestra un mini-calendario 7 días × franjas horarias
 * con las sesiones de repaso proyectadas por SM-2.
 */
class ForgetCurveCalendarFragment : Fragment() {

    companion object {
        fun newInstance() = ForgetCurveCalendarFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_forget_curve_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderCalendar(view)
    }

    private fun renderCalendar(root: View) {
        val grid = root.findViewById<GridLayout>(R.id.forget_curve_grid)
        grid.removeAllViews()

        // Datos de ejemplo — reemplazar con consulta real a la BD de Anki
        val mockIntervals = listOf(
            Pair(0, 9), Pair(0, 9), Pair(1, 10),
            Pair(1, 10), Pair(2, 8), Pair(3, 9),
            Pair(3, 9), Pair(3, 9), Pair(5, 11),
            Pair(6, 8), Pair(6, 8)
        )

        val sessions = ForgetCurveScheduler.projectSessions(mockIntervals)
        val sessionMap = sessions.associateBy { Pair(it.dayOffset, it.hour) }

        val hours = listOf(8, 9, 10, 11, 12, 18, 20)
        val days  = 7

        // Encabezados de días
        grid.columnCount = days + 1
        addCell(grid, "") // esquina vacía
        for (d in 0 until days) {
            addCell(grid, ForgetCurveScheduler.dayLabel(d), header = true)
        }

        // Filas por hora
        for (h in hours) {
            addCell(grid, "${h}h", header = true)
            for (d in 0 until days) {
                val session = sessionMap[Pair(d, h)]
                val label   = if (session != null) "${session.cardCount}✦" else "·"
                val highlight = session != null
                addCell(grid, label, highlight = highlight)
            }
        }
    }

    private fun addCell(
        grid: GridLayout,
        text: String,
        header: Boolean = false,
        highlight: Boolean = false
    ) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            textSize  = if (header) 10f else 9f
            gravity   = android.view.Gravity.CENTER
            setPadding(4, 4, 4, 4)
            setTypeface(null, if (header) android.graphics.Typeface.BOLD
                               else android.graphics.Typeface.NORMAL)
            if (highlight) setBackgroundResource(R.color.material_blue_600)
        }
        val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        tv.layoutParams = GridLayout.LayoutParams(spec, spec).apply {
            width  = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
        }
        grid.addView(tv)
    }
}
