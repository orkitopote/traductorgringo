package com.ejemplo.subtitulos

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class Line(val english: String, var spanish: String)

class SubtitleAdapter : RecyclerView.Adapter<SubtitleAdapter.Holder>() {

    private val lines = mutableListOf<Line>()
    var textScale = 1f
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val en: TextView = view.findViewById(R.id.tvEn)
        val es: TextView = view.findViewById(R.id.tvEs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_subtitle, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = lines[position]
        holder.en.text = line.english
        holder.es.text = line.spanish
        holder.en.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * textScale)
        holder.es.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * textScale)
    }

    override fun getItemCount() = lines.size

    fun add(line: Line) {
        lines.add(line)
        notifyItemInserted(lines.size - 1)
    }

    fun refresh(line: Line) {
        val index = lines.indexOf(line)
        if (index >= 0) notifyItemChanged(index)
    }

    fun clear() {
        val n = lines.size
        lines.clear()
        notifyItemRangeRemoved(0, n)
    }

    fun asText(): String = lines.joinToString("\n\n") { "${it.english}\n${it.spanish}" }
}
