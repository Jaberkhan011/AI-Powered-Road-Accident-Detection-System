package com.example.crashguard.addcontact


import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object ContactsHelper {

    private const val PREF = "accident_contacts"
    private const val KEY = "contacts_json"
    private const val KEY_SELECTED = "contacts_selected" // stores JSON array of phone strings

    fun loadContactsFromPhone(ctx: Context, resolver: ContentResolver): List<Contact> {
        val out = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: ""
                val numberRaw = it.getString(numIdx) ?: ""
                val number = numberRaw.replace(Regex("[\\s-()]"), "")
                if (number.isNotBlank()) out.add(Contact(name, number))
            }
        }

        return out
    }

    fun saveContacts(ctx: Context, contacts: List<Contact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            val o = JSONObject()
            o.put("name", c.name)
            o.put("phone", c.phone)
            arr.put(o)
        }

        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putString(KEY, arr.toString())
            }
    }

    fun getSavedContacts(ctx: Context): List<Contact> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()

        val arr = JSONArray(raw)
        val out = mutableListOf<Contact>()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Contact(o.getString("name"), o.getString("phone")))
        }
        return out
    }

    // Selected phones (persist phone numbers of contacts chosen to receive alerts)
    fun saveSelectedPhones(ctx: Context, phones: List<String>) {
        val arr = JSONArray()
        phones.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SELECTED, arr.toString())
            }
    }

    fun getSelectedPhones(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }
}