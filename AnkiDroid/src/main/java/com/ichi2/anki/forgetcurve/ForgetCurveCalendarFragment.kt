package com.ichi2.anki.forgetcurve

import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment

class ForgetCurveCalendarFragment : Fragment() {

    companion object {
        fun newInstance() = ForgetCurveCalendarFragment()
        private const val COL_WIDTH_DP  = 90
        private const val HOUR_WIDTH_DP = 44
        private const val ROW_HEIGHT_DP = 52
        private const val HEADER_HEIGHT_DP = 42
    }

    private var sessions: List<ForgetCurveScheduler.ReviewSession> = emptyList()
    private var allDecks: List<String> = emptyList()
    private var filteredDecks: MutableSet<String> = mutableSetOf()
    private lateinit var gridContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(0, 8, 0, 8)
        }

        // ── Encabezado ──────────────────────────────────────────
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 0, 12, 6)
        }
        val title = TextView(requireContext()).apply {
            text = "📅 Curva del olvido"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnFilter = Button(requireContext()).apply {
            text = "🎯 Mazos"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1565C0"))
            setPadding(16, 4, 16, 4)
            setOnClickListener { showDeckFilterDialog() }
        }
        header.addView(title)
        header.addView(btnFilter)
        root.addView(header)

        // ── Leyenda de colores ───────────────────────────────────
        val legendScroll = HorizontalScrollView(requireContext()).apply {
            setPadding(12, 0, 12, 6)
        }
        val legendRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        legendScroll.addView(legendRow)
        root.addView(legendScroll)

        // ── Grid con scroll horizontal + vertical ────────────────
        val hScroll = HorizontalScrollView(requireContext())
        val vScroll = ScrollView(requireContext())
        gridContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        vScroll.addView(gridContainer)
        hScroll.addView(vScroll)
        hScroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 320.dp
        )
        root.addView(hScroll)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAndRender()
    }

    private fun loadAndRender() {
        sessions  = ForgetCurveScheduler.projectSessions()
        allDecks  = sessions.map { it.deckName }.distinct().sorted()
        if (filteredDecks.isEmpty()) filteredDecks.addAll(allDecks)
        updateLegend()
        renderGrid()
    }

    private fun updateLegend() {
        val root = view ?: return
        val legendRow = (root.getChildAt(1) as? HorizontalScrollView)
            ?.getChildAt(0) as? LinearLayout ?: return
        legendRow.removeAllViews()
        for (deck in allDecks) {
            val color = ForgetCurveScheduler.colorForDeck(deck, allDecks)
            val chip = TextView(requireContext()).apply {
                text = "● $deck"
                textSize = 9f
                setTextColor(color)
                setPadding(8, 2, 12, 2)
            }
            legendRow.addView(chip)
        }
    }

    private fun renderGrid() {
        gridContainer.removeAllViews()
        val ctx   = requireContext()
        val days  = 7
        val hours = (0..23).toList()

        val activeSessions = sessions.filter { it.deckName in filteredDecks }
        // (día, hora) → lista de sesiones
        val map = mutableMapOf<Pair<Int,Int>, MutableList<ForgetCurveScheduler.ReviewSession>>()
        for (s in activeSessions) {
            map.getOrPut(Pair(s.dayOffset, s.hour)) { mutableListOf() }.add(s)
        }

        // ── Fila de encabezado de días ───────────────────────────
        val dayHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        // Celda esquina
        dayHeaderRow.addView(makeLabelCell(ctx, "", HOUR_WIDTH_DP.dp, HEADER_HEIGHT_DP.dp))
        // Separador vertical
        dayHeaderRow.addView(makeDividerV(ctx))

        for (d in 0 until days) {
            dayHeaderRow.addView(makeDayHeader(ctx, d))
            if (d < days - 1) dayHeaderRow.addView(makeDividerV(ctx))
        }
        gridContainer.addView(dayHeaderRow)
        gridContainer.addView(makeDividerH(ctx))

        // ── Filas de horas ───────────────────────────────────────
        for (h in hours) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = ROW_HEIGHT_DP.dp
            }
            // Etiqueta hora
            val ampm = when {
                h == 0  -> "12\nam"
                h < 12  -> "$h\nam"
                h == 12 -> "12\npm"
                else    -> "${h-12}\npm"
            }
            row.addView(makeLabelCell(ctx, ampm, HOUR_WIDTH_DP.dp, ROW_HEIGHT_DP.dp))
            row.addView(makeDividerV(ctx))

            for (d in 0 until days) {
                val entries = map[Pair(d, h)]
                row.addView(makeEventCell(ctx, entries))
                if (d < days - 1) row.addView(makeDividerV(ctx))
            }
            gridContainer.addView(row)
            gridContainer.addView(makeDividerH(ctx))
        }
    }

    // ── Celdas ──────────────────────────────────────────────────

    private fun makeDayHeader(ctx: android.content.Context, day: Int): TextView {
        return TextView(ctx).apply {
            text = ForgetCurveScheduler.dayLabel(day)
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(COL_WIDTH_DP.dp, HEADER_HEIGHT_DP.dp)
        }
    }

    private fun makeLabelCell(
        ctx: android.content.Context, text: String, w: Int, h: Int
    ): TextView {
        return TextView(ctx).apply {
            this.text  = text
            textSize   = 9f
            gravity    = Gravity.CENTER
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(w, h)
        }
    }

    private fun makeEventCell(
        ctx: android.content.Context,
        entries: List<ForgetCurveScheduler.ReviewSession>?
    ): LinearLayout {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(COL_WIDTH_DP.dp, ROW_HEIGHT_DP.dp)
            setPadding(2, 2, 2, 2)
        }
        if (entries.isNullOrEmpty()) return cell

        for (s in entries) {
            val color = ForgetCurveScheduler.colorForDeck(s.deckName, allDecks)
            val eventView = TextView(ctx).apply {
                text = "${s.deckName}\n${s.cardCount} tarjetas"
                textSize = 7.5f
                setTextColor(Color.WHITE)
                setBackgroundColor(color)
                setPadding(4, 2, 4, 2)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 2 }
            }
            cell.addView(eventView)
        }
        return cell
    }

    private fun makeDividerV(ctx: android.content.Context): View {
        return View(ctx).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun makeDividerH(ctx: android.content.Context): View {
        return View(ctx).apply {
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        }
    }

    // ── Diálogo filtro mazos ─────────────────────────────────────

    private fun showDeckFilterDialog() {
        if (allDecks.isEmpty()) return
        val checked = allDecks.map { it in filteredDecks }.toBooleanArray()
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Selecciona mazos a mostrar")
            .setMultiChoiceItems(allDecks.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) filteredDecks.add(allDecks[which])
                else filteredDecks.remove(allDecks[which])
            }
            .setPositiveButton("Aplicar") { _, _ ->
                updateLegend()
                renderGrid()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
