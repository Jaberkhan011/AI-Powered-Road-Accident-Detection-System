package com.example.crashguard.addcontact

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.crashguard.R

class ContactAdapter(
    // Change 'val' to 'var' to make the list replaceable
    private var items: MutableList<Contact>,
    // provide initial selected phones set (mutable)
    private val selectedPhones: MutableSet<String>,
    // on list changed (after delete) -> persist externally
    private val onListChanged: (MutableList<Contact>) -> Unit,
    // on selection changed -> persist externally
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvName.text = c.name
        holder.tvPhone.text = c.phone

        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = selectedPhones.contains(c.phone)

        holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedPhones.add(c.phone) else selectedPhones.remove(c.phone)
            onSelectionChanged(selectedPhones)
        }

        holder.btnDelete.setOnClickListener {
            // get the adapter position, which is reliable inside a click listener
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                val contactToRemove = items[currentPosition]
                // remove from list; also remove from selectedPhones if present
                selectedPhones.remove(contactToRemove.phone)
                items.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
                onSelectionChanged(selectedPhones)
                onListChanged(items)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // Add this function to update the contacts list from the search filter
    fun updateContacts(newContacts: MutableList<Contact>) {
        items = newContacts
        notifyDataSetChanged()
    }
}