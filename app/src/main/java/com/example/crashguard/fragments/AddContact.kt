package com.example.crashguard.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crashguard.addcontact.Contact
import com.example.crashguard.addcontact.ContactAdapter
import com.example.crashguard.addcontact.ContactsHelper
import com.example.crashguard.R

class AddContact : Fragment() {

    private lateinit var tvSelectedList: TextView
    private lateinit var searchViewContacts: SearchView // Declare SearchView

    private lateinit var rvContacts: RecyclerView

    private var contactsList = mutableListOf<Contact>()
    private var selectedPhones = mutableSetOf<String>()
    private var adapter: ContactAdapter? = null
    private val REQ_PERMS = 101
    private val REQUIRED_PERMS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_add_contact, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchViewContacts = view.findViewById(R.id.searchViewContacts)
        tvSelectedList = view.findViewById(R.id.tvSelectedList)
        rvContacts = view.findViewById(R.id.rvContacts)
        rvContacts.layoutManager = LinearLayoutManager(requireContext())

        contactsList = ContactsHelper.getSavedContacts(requireContext()).toMutableList()
        val savedSelected = ContactsHelper.getSelectedPhones(requireContext())
        selectedPhones = savedSelected.toMutableSet()

        adapter = ContactAdapter(contactsList, selectedPhones, { updatedList ->
            // persist contact list when changed (delete)
            ContactsHelper.saveContacts(requireContext(), updatedList)
        }, { sel ->
            // persist selection whenever it changes
            ContactsHelper.saveSelectedPhones(requireContext(), sel.toList())
            updateSelectedListText()
        })

        rvContacts.adapter = adapter
        updateSelectedListText()

        // Setup search listener
        searchViewContacts.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterContacts(newText)
                return true
            }
        })
    }

    private fun filterContacts(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            contactsList
        } else {
            contactsList.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.phone.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        adapter?.updateContacts(filteredList)
    }
    private fun updateSelectedListText() {
        if (selectedPhones.isEmpty()) {
            tvSelectedList.text = "No contacts selected"
        } else {
            // Show names and phones for selected contacts
            val chosen = contactsList.filter { selectedPhones.contains(it.phone) }
            val text = chosen.joinToString(separator = "\n") { "${it.name} : ${it.phone}" }
            tvSelectedList.text = text
        }
    }
    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(rc: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (rc == REQ_PERMS) {
            if (hasPermissions()) {
                // reload contacts and selection because READ_CONTACTS might now be granted
                contactsList = ContactsHelper.getSavedContacts(requireContext()).toMutableList()
                adapter = ContactAdapter(contactsList, selectedPhones, { updated ->
                    ContactsHelper.saveContacts(requireContext(), updated)
                }, { sel ->
                    ContactsHelper.saveSelectedPhones(requireContext(), sel.toList())
                    updateSelectedListText()
                })
                rvContacts.adapter = adapter
//                startDetection()
            } else {
                Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }
        super.onRequestPermissionsResult(rc, permissions, grantResults)
    }

}