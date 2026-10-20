package com.example.crashguard.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crashguard.alerthistory.AlertHistoryAdapter
import com.example.crashguard.alerthistory.AlertHistoryHelper
import com.example.crashguard.R

class AlertHistory : Fragment() {
    private lateinit var rvAlertHistory: RecyclerView
    private lateinit var adapter: AlertHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alert_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvAlertHistory = view.findViewById(R.id.rvAlertHistory)
        rvAlertHistory.layoutManager = LinearLayoutManager(requireContext())

        val history = AlertHistoryHelper.getHistory(requireContext())
        adapter = AlertHistoryAdapter(history) { record ->
            AlertHistoryHelper.deleteAlert(requireContext(), record.id)
            refreshData()
        }
        rvAlertHistory.adapter = adapter
    }

    private fun refreshData() {
        val updatedHistory = AlertHistoryHelper.getHistory(requireContext())
        adapter.updateData(updatedHistory)
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }
}
