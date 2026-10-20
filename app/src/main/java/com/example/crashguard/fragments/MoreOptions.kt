package com.example.crashguard.fragments

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.crashguard.AuthManager
import com.example.crashguard.R
import com.google.android.material.button.MaterialButton

class MoreOptions : Fragment() {

    private lateinit var switchMode: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_more_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aboutProject = view.findViewById<Button>(R.id.About)
        val logOut = view.findViewById<Button>(R.id.Logout)
        switchMode = view.findViewById(R.id.SwitchMode)

        // Initial state for Switch Mode button
        updateSwitchModeUI()

        aboutProject.setOnClickListener {
            // Expanded AboutProject Fragment same as in MainActivity
            parentFragmentManager.beginTransaction()
                .replace(R.id.frameLayout, AboutProject())
                .addToBackStack(null)
                .commit()
        }

        logOut.setOnClickListener {
            AuthManager.clearLogin(requireContext())
            val intent = requireActivity().packageManager
                .getLaunchIntentForPackage(requireActivity().packageName)
            intent?.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
            )
            startActivity(intent)
            requireActivity().finish()
        }

        switchMode.setOnClickListener {
            val isDark = isDarkMode()
            if (isDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSwitchModeUI()
    }

    private fun updateSwitchModeUI() {
        if (::switchMode.isInitialized) {
            val isDark = isDarkMode()
            if (isDark) {
                switchMode.text = getString(R.string.switch_mode)
                switchMode.setIconResource(R.drawable.light_mode_24)
            } else {
                switchMode.text = getString(R.string.switch_mode)
                switchMode.setIconResource(R.drawable.outline_dark_mode_24)
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }
}
