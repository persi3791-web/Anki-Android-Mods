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

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.launchCatchingTask

import kotlinx.coroutines.launch


class ForgetCurveCalendarFragment : Fragment() {

    companion object {
        fun newInstance() = ForgetCurveCalendarFragment()
        private const val COL_WIDTH_DP  = 100
        private const val HOUR_WIDTH_DP = 48
        private const val ROW_HEIGHT_DP = 56
        private const val HEADER_HEIGHT_DP = 48
    }

    private var sessions: List<ForgetCurveScheduler.ReviewSession> = emptyList()
    private var deckTree: MutableMap<String, Boolean> = mutableMapOf()
    private var deckCollapsed: MutableMap<String, Boolean> = mutableMapOf()
    private lateinit var gridContainer: LinearLayout
    private lateinit var rootLayout: LinearLayout
    private lateinit var loadingText: TextView
    private var isFullscreen = false
    private var fullscreenBtn: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(0, 8, 0, 8)
        }

        // ── Header ──────────────────────────────────────────────────────────
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 0, 12, 6)
        }
        val title = TextView(requireContext()).apply {
            text = "Curva del olvido"
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnFilter = Button(requireContext()).apply {
            text = "Mazos"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1565C0"))
            setPadding(16, 4, 16, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 8 }
            setOnClickListener { showDeckTreeDialog() }
        }
        val btnFullscreen = Button(requireContext()).apply {
            text = "⛶"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(16, 4, 16, 4)
            setOnClickListener { toggleFullscreen(this) }
        }
        fullscreenBtn = btnFullscreen
        header.addView(title)
        header.addView(btnFilter)
        header.addView(btnFullscreen)
        rootLayout.addView(header)

        // ── Leyenda ──────────────────────────────────────────────────────────
        val legendScroll = HorizontalScrollView(requireContext()).apply {
            tag = "legendScroll"
            setPadding(12, 0, 12, 6)
        }
        val legendRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        legendScroll.addView(legendRow)
        rootLayout.addView(legendScroll)

        // ── Loading ──────────────────────────────────────────────────────────
        loadingText = TextView(requireContext()).apply {
            text = "Cargando mazos..."
            textSize = 11f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 16.dp }
        }
        rootLayout.addView(loadingText)

        // ── Grid ─────────────────────────────────────────────────────────────
        val hScroll = HorizontalScrollView(requireContext())
        val vScroll = ScrollView(requireContext())
        gridContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        vScroll.addView(gridContainer)
        hScroll.addView(vScroll)
        hScroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        )
        rootLayout.addView(hScroll)
        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAndRender()
    }

    // ── Pantalla completa ────────────────────────────────────────────────────
    private fun toggleFullscreen(btn: Button) {
        val act = activity ?: return
        isFullscreen = !isFullscreen
        val window = act.window
        if (isFullscreen) {
            (act as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            view?.rootView?.findViewById<android.view.View>(
                com.ichi2.anki.R.id.pull_to_sync_wrapper
            )?.visibility = android.view.View.GONE
            view?.rootView?.requestLayout()
            btn.text = "✕"
        } else {
            (act as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.show()
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            view?.rootView?.findViewById<android.view.View>(
                com.ichi2.anki.R.id.pull_to_sync_wrapper
            )?.visibility = android.view.View.VISIBLE
            view?.rootView?.requestLayout()
            btn.text = "⛶"
        }
    }

    // ── Carga en background ──────────────────────────────────────────────────
    private fun loadAndRender() {
        launchCatchingTask {
            // Cargar BD en hilo IO
            val loaded = withCol {
                ForgetCurveScheduler.projectSessions(this)
                
            }
            // Actualizar UI en hilo principal
            sessions = loaded
            loadingText.visibility = View.GONE

            val allPaths = mutableSetOf<String>()
            for (s in sessions) {
                val parts = s.fullDeckPath.split("::")
                for (i in parts.indices) {
                    allPaths.add(parts.take(i + 1).joinToString("::"))
                }
            }
            for (path in allPaths) {
                if (!deckTree.containsKey(path)) deckTree[path] = true
            }
            deckTree.keys.retainAll(allPaths)
            updateLegend()
            renderGrid()
        }
    }

    // ── Leyenda ──────────────────────────────────────────────────────────────
    private fun updateLegend() {
        val root = view ?: return
        val legendRow = root
            .findViewWithTag<HorizontalScrollView>("legendScroll")
            ?.getChildAt(0) as? LinearLayout ?: return
        legendRow.removeAllViews()
        val leafDecks = sessions.map { it.fullDeckPath }.distinct().sorted()
        for (deck in leafDecks) {
            val color = ForgetCurveScheduler.colorForDeck(deck)
            val chip = TextView(requireContext()).apply {
                text = "● ${deck.substringAfterLast("::")}"
                textSize = 9f
                setTextColor(color)
                setPadding(8, 2, 12, 2)
            }
            legendRow.addView(chip)
        }
    }

    // ── Grid ─────────────────────────────────────────────────────────────────
    private fun renderGrid() {
        gridContainer.removeAllViews()
        val ctx = requireContext()
        val days = 7
        val hours = (0..23).toList()

        val visibleDecks = sessions
            .map { it.fullDeckPath }
            .distinct()
            .filter { isPathVisible(it) }
            .toSet()

        val activeSessions = sessions.filter { it.fullDeckPath in visibleDecks }
        val leafDecks = sessions.map { it.fullDeckPath }.distinct().sorted()

        val map = mutableMapOf<Pair<Int,Int>, MutableList<ForgetCurveScheduler.ReviewSession>>()
        for (s in activeSessions) {
            map.getOrPut(Pair(s.dayOffset, s.hour)) { mutableListOf() }.add(s)
        }

        // Cabecera días
        val dayHeaderRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        dayHeaderRow.addView(makeLabelCell(ctx, "", HOUR_WIDTH_DP.dp, HEADER_HEIGHT_DP.dp))
        dayHeaderRow.addView(makeDividerV(ctx))
        for (d in 0 until days) {
            dayHeaderRow.addView(makeDayHeader(ctx, d))
            if (d < days - 1) dayHeaderRow.addView(makeDividerV(ctx))
        }
        gridContainer.addView(dayHeaderRow)
        gridContainer.addView(makeDividerH(ctx))

        // Filas horas
        for (h in hours) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = ROW_HEIGHT_DP.dp
            }
            val ampm = when {
                h == 0  -> "12\nam"
                h < 12  -> "$h\nam"
                h == 12 -> "12\npm"
                else    -> "${h - 12}\npm"
            }
            row.addView(makeLabelCell(ctx, ampm, HOUR_WIDTH_DP.dp, ROW_HEIGHT_DP.dp))
            row.addView(makeDividerV(ctx))
            for (d in 0 until days) {
                row.addView(makeEventCell(ctx, map[Pair(d, h)], leafDecks))
                if (d < days - 1) row.addView(makeDividerV(ctx))
            }
            gridContainer.addView(row)
            gridContainer.addView(makeDividerH(ctx))
        }
    }

    private fun isPathVisible(path: String): Boolean {
        val parts = path.split("::")
        for (i in parts.indices) {
            if (deckTree[parts.take(i + 1).joinToString("::")] == false) return false
        }
        return true
    }

    // ── Diálogo árbol mazos ───────────────────────────────────────────────────
    private fun showDeckTreeDialog() {
        if (deckTree.isEmpty()) return
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        scroll.addView(container)
        buildTreeUI(container)
        android.app.AlertDialog.Builder(ctx)
            .setTitle("Selecciona mazos")
            .setView(scroll)
            .setPositiveButton("Aplicar") { _, _ -> updateLegend(); renderGrid() }
            .setNeutralButton("Todos") { _, _ ->
                deckTree.keys.forEach { deckTree[it] = true }
                updateLegend(); renderGrid()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun buildTreeUI(container: LinearLayout) {
        container.removeAllViews()
        val ctx = requireContext()
        for (path in deckTree.keys.sorted()) {
            val parts = path.split("::")
            val depth = parts.size - 1
            val hasChildren = deckTree.keys.any { it.startsWith("$path::") }
            var ancestorCollapsed = false
            for (i in 0 until depth) {
                if (deckCollapsed[parts.take(i + 1).joinToString("::")] == true) {
                    ancestorCollapsed = true; break
                }
            }
            if (ancestorCollapsed) continue

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setPadding(depth * 32, 4, 4, 4)
            }
            val btnToggle = TextView(ctx).apply {
                text = if (hasChildren) { if (deckCollapsed[path] == true) "▶" else "▼" } else "  "
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(0, 0, 8, 0)
                if (hasChildren) {
                    setOnClickListener {
                        deckCollapsed[path] = deckCollapsed[path] != true
                        buildTreeUI(container)
                    }
                }
            }
            val checkBox = CheckBox(ctx).apply {
                text = parts.last()
                isChecked = deckTree[path] ?: true
                textSize = 11f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, checked ->
                    deckTree[path] = checked
                    deckTree.keys.filter { it.startsWith("$path::") }.forEach { deckTree[it] = checked }
                    buildTreeUI(container)
                }
            }
            row.addView(btnToggle)
            row.addView(checkBox)
            container.addView(row)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun makeDayHeader(ctx: android.content.Context, day: Int) =
        TextView(ctx).apply {
            text = ForgetCurveScheduler.dayLabel(day)
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(COL_WIDTH_DP.dp, HEADER_HEIGHT_DP.dp)
        }

    private fun makeLabelCell(ctx: android.content.Context, text: String, w: Int, h: Int) =
        TextView(ctx).apply {
            this.text = text
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(w, h)
        }

    private fun makeEventCell(
        ctx: android.content.Context,
        entries: List<ForgetCurveScheduler.ReviewSession>?,
        leafDecks: List<String>,
    ): LinearLayout {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(COL_WIDTH_DP.dp, ROW_HEIGHT_DP.dp)
            setPadding(2, 2, 2, 2)
        }
        if (entries.isNullOrEmpty()) return cell
        cell.setOnClickListener {
            val deckId = entries.first().deckId
            launchCatchingTask {
                withCol { decks.select(deckId) }
                startActivity(android.content.Intent(
                    requireContext(), com.ichi2.anki.Reviewer::class.java))
            }
        }
        for (s in entries) {
            val color = ForgetCurveScheduler.colorForDeck(s.fullDeckPath)
            val mins = (s.cardCount * 1.5).toInt()
            val label = s.fullDeckPath.substringAfterLast("::")
            cell.addView(TextView(ctx).apply {
                text = "$label\n${s.cardCount} tarj · ${mins}min"
                textSize = 7.5f
                setTextColor(Color.WHITE)
                setBackgroundColor(color)
                setPadding(4, 2, 4, 2)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = 2 }
            })
        }
        return cell
    }

    private fun makeDividerV(ctx: android.content.Context) = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun makeDividerH(ctx: android.content.Context) = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
