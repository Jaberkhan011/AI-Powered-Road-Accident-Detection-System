package com.example.crashguard.alerthistory

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.crashguard.R

class AlertHistoryAdapter(
    private var history: List<AlertRecord>,
    private val onDelete: (AlertRecord) -> Unit
) : RecyclerView.Adapter<AlertHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnLocation: ImageButton = view.findViewById(R.id.btnLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = history[position]
        holder.tvName.text = record.type
        holder.tvTime.text = record.time
        holder.tvLocation.text = record.location
        holder.btnDelete.setOnClickListener {
            onDelete(record)
        }
        holder.btnLocation.setOnClickListener {
            val url = record.location

            if (url.startsWith("https://")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            it.context.startActivity(intent)
        }else{
                Toast.makeText(it.context, "Location Unknown", Toast.LENGTH_SHORT).show()
        }
        }
    }

    override fun getItemCount(): Int = history.size

    fun updateData(newHistory: List<AlertRecord>) {
        history = newHistory
        notifyDataSetChanged()
    }
}