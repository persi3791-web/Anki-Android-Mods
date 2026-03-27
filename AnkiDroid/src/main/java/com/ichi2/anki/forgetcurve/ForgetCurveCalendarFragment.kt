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

class ForgetCurveCalendarFragment : Fragment() {

    companion object {
        fun newInstance() = ForgetCurveCalendarFragment()
        private const val COL_WIDTH_DP = 100
        private const val HOUR_WIDTH_DP = 48
        private const val ROW_HEIGHT_DP = 56
        private const val HEADER_HEIGHT_DP = 48
    }

    private var sessions: List<ForgetCurveScheduler.ReviewSession> = emptyList()
    private var deckTree: MutableMap<String, Boolean> = mutableMapOf()
    private var deckCollapsed: MutableMap<String, Boolean> = mutableMapOf()
    private lateinit var gridContainer: LinearLayout
    private lateinit var rootLayout: LinearLayout
    private var isFullscreen = false

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
        header.addView(title)
        header.addView(btnFilter)
        header.addView(btnFullscreen)
        rootLayout.addView(header)
        val legendScroll = HorizontalScrollView(requireContext()).apply {
            tag = "legendScroll"
            setPadding(12, 0, 12, 6)
        }
        val legendRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        legendScroll.addView(legendRow)
        rootLayout.addView(legendScroll)
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

    private fun toggleFullscreen(btn: Button) {
        isFullscreen = !isFullscreen
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, !isFullscreen)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            btn.text = "✕"
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            btn.text = "⛶"
        }
    }

    private fun loadAndRender() {
        sessions = ForgetCurveScheduler.projectSessions()
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

    private fun updateLegend() {
        val root = view ?: return
        val legendRow = root
            .findViewWithTag<HorizontalScrollView>("legendScroll")
            ?.getChildAt(0) as? LinearLayout ?: return
        legendRow.removeAllViews()
        val leafDecks = sessions.map { it.fullDeckPath }.distinct().sorted()
        for (deck in leafDecks) {
            val color = ForgetCurveScheduler.colorForDeck(deck, leafDecks)
            val chip = TextView(requireContext()).apply {
                text = "● ${deck.substringAfterLast("::")}"
                textSize = 9f
                setTextColor(color)
                setPadding(8, 2, 12, 2)
            }
            legendRow.addView(chip)
        }
    }

    private fun renderGrid() {
        gridContainer.removeAllViews()
        val ctx = requireContext()
        val days = 7
        val hours = (0..23).toList()
        val visibleDecks = sessions
            .map { it.fullDeckPath }
            .distinct()
            .filter { path -> isPathVisible(path) }
            .toSet()
        val activeSessions = sessions.filter { it.fullDeckPath in visibleDecks }
        val leafDecks = sessions.map { it.fullDeckPath }.distinct().sorted()
        val map = mutableMapOf<Pair<Int, Int>, MutableList<ForgetCurveScheduler.ReviewSession>>()
        for (s in activeSessions) {
            map.getOrPut(Pair(s.dayOffset, s.hour)) { mutableListOf() }.add(s)
        }
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
        for (h in hours) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = ROW_HEIGHT_DP.dp
            }
            val ampm = when {
                h == 0 -> "12\nam"
                h < 12 -> "$h\nam"
                h == 12 -> "12\npm"
                else -> "${h - 12}\npm"
            }
            row.addView(makeLabelCell(ctx, ampm, HOUR_WIDTH_DP.dp, ROW_HEIGHT_DP.dp))
            row.addView(makeDividerV(ctx))
            for (d in 0 until days) {
                val entries = map[Pair(d, h)]
                row.addView(makeEventCell(ctx, entries, leafDecks))
                if (d < days - 1) row.addView(makeDividerV(ctx))
            }
            gridContainer.addView(row)
            gridContainer.addView(makeDividerH(ctx))
        }
    }

    private fun isPathVisible(path: String): Boolean {
        val parts = path.split("::")
        for (i in parts.indices) {
            val ancestor = parts.take(i + 1).joinToString("::")
            if (deckTree[ancestor] == false) return false
        }
        return true
    }

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
        android.app.AlertDialog
            .Builder(ctx)
            .setTitle("Selecciona mazos")
            .setView(scroll)
            .setPositiveButton("Aplicar") { _, _ ->
                updateLegend()
                renderGrid()
            }
            .setNeutralButton("Todos") { _, _ ->
                deckTree.keys.forEach { deckTree[it] = true }
                updateLegend()
                renderGrid()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun buildTreeUI(container: LinearLayout) {
        container.removeAllViews()
        val ctx = requireContext()
        val sorted = deckTree.keys.sorted()
        for (path in sorted) {
            val parts = path.split("::")
            val depth = parts.size - 1
            val name = parts.last()
            val hasChildren = deckTree.keys.any { it.startsWith("$path::") }
            var ancestorCollapsed = false
            for (i in 0 until depth) {
                val ancestor = parts.take(i + 1).joinToString("::")
                if (deckCollapsed[ancestor] == true) { ancestorCollapsed = true; break }
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
                text = name
                isChecked = deckTree[path] ?: true
                textSize = 11f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, isChecked ->
                    deckTree[path] = isChecked
                    val prefix = "$path::"
                    deckTree.keys.filter { it.startsWith(prefix) }.forEach { deckTree[it] = isChecked }
                    buildTreeUI(container)
                }
            }
            row.addView(btnToggle)
            row.addView(checkBox)
            container.addView(row)
        }
    }

    private fun makeDayHeader(ctx: android.content.Context, day: Int): TextView =
        TextView(ctx).apply {
            text = ForgetCurveScheduler.dayLabel(day)
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(COL_WIDTH_DP.dp, HEADER_HEIGHT_DP.dp)
        }

    private fun makeLabelCell(ctx: android.content.Context, text: String, w: Int, h: Int): TextView =
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
        for (s in entries) {
            val color = ForgetCurveScheduler.colorForDeck(s.fullDeckPath, leafDecks)
            val minutos = (s.cardCount * 1.5).toInt()
            val label = s.fullDeckPath.substringAfterLast("::")
            val eventView = TextView(ctx).apply {
                text = "$label\n${s.cardCount} tarj · ${minutos}min"
                textSize = 7.5f
                setTextColor(Color.WHITE)
                setBackgroundColor(color)
                setPadding(4, 2, 4, 2)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = 2 }
            }
            cell.addView(eventView)
        }
        return cell
    }

    private fun makeDividerV(ctx: android.content.Context): View = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun makeDividerH(ctx: android.content.Context): View = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#2A2A2A"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
