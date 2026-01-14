package com.example.myapplicationv10

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplicationv10.databinding.ActivityProfileBinding
import com.example.myapplicationv10.network.NetworkResult
import com.example.myapplicationv10.utils.Constants
import com.example.myapplicationv10.utils.ValveLimitManager
import com.example.myapplicationv10.viewmodel.ProfileViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var editProfileLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewModel: ProfileViewModel
    private var currentAvatarUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        editProfileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.loadUserProfile()
            }
        }

        setupBackButton()
        setupTabNavigation()
        setupLogout()
        setupEditButton()
        observeProfileState()
        loadValveLimit()

        viewModel.loadUserProfile()
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupTabNavigation() {
        binding.personalInfoTab.setOnClickListener {
            binding.personalInfoTab.setTextColor(getColor(R.color.black))
            binding.personalInfoTab.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.teamsTab.setTextColor(android.graphics.Color.parseColor("#999999"))
            binding.teamsTab.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.personalInfoSection.visibility = View.VISIBLE
            binding.systemSection.visibility = View.GONE
        }

        binding.teamsTab.setOnClickListener {
            binding.teamsTab.setTextColor(getColor(R.color.black))
            binding.teamsTab.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.personalInfoTab.setTextColor(android.graphics.Color.parseColor("#999999"))
            binding.personalInfoTab.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.personalInfoSection.visibility = View.GONE
            binding.systemSection.visibility = View.VISIBLE
        }
    }

    private fun setupEditButton() {
        binding.editButton.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)

            intent.putExtra("firstName", binding.firstNameValue.text.toString())
            intent.putExtra("lastName", binding.lastNameValue.text.toString())
            intent.putExtra("dateOfBirth", binding.dateOfBirthValue.text.toString())
            intent.putExtra("email", binding.emailValue.text.toString())
            intent.putExtra("phoneNumber", binding.phoneNumberValue.text.toString())
            intent.putExtra("location", binding.locationValue.text.toString())
            intent.putExtra("numberOfValves", binding.numberOfValvesValue.text.toString().replace(" valves to manage", "").toIntOrNull() ?: 8)
            intent.putExtra("avatarUrl", currentAvatarUrl)

            editProfileLauncher.launch(intent)
        }
    }

    private fun setupLogout() {
        binding.logoutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ -> performLogout() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun performLogout() {
        com.example.myapplicationv10.utils.TokenManager.getInstance(this).logout()

        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        prefs.edit().clear().apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun loadValveLimit() {
        val valveLimitManager = ValveLimitManager.getInstance(this)
        val limit = valveLimitManager.getValveLimit()
        binding.numberOfValvesValue.text = "$limit valves to manage"
    }

    private fun observeProfileState() {
        lifecycleScope.launch {
            viewModel.profileState.collect { result ->
                when (result) {
                    is NetworkResult.Idle -> {
                        binding.loadingIndicator.visibility = View.GONE
                    }

                    is NetworkResult.Loading -> {
                        binding.loadingIndicator.visibility = View.VISIBLE
                    }

                    is NetworkResult.Success -> {
                        binding.loadingIndicator.visibility = View.GONE

                        val user = result.data

                        currentAvatarUrl = Constants.fixAvatarUrl(user.avatarUrl)

                        binding.firstNameValue.text = user.firstName ?: "N/A"
                        binding.lastNameValue.text = user.lastName ?: "N/A"
                        binding.emailValue.text = user.email
                        binding.phoneNumberValue.text = user.phoneNumber ?: "N/A"
                        binding.locationValue.text = user.location ?: "N/A"
                        binding.dateOfBirthValue.text = formatDateForDisplay(user.dateOfBirth)

                        val fullName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
                        binding.userFullName.text = fullName.ifEmpty { "User" }
                        binding.userEmailHeader.text = user.email

                        loadAvatar(currentAvatarUrl)

                        val valveLimitManager = ValveLimitManager.getInstance(this@ProfileActivity)
                        val currentLimit = valveLimitManager.getValveLimit()
                        binding.numberOfValvesValue.text = "$currentLimit valves to manage"
                    }

                    is NetworkResult.Error -> {
                        binding.loadingIndicator.visibility = View.GONE
                        Snackbar.make(
                            binding.root,
                            "Failed to load profile: ${result.message}",
                            Snackbar.LENGTH_LONG
                        ).setAction("Retry") {
                            viewModel.loadUserProfile()
                        }.show()
                    }
                }
            }
        }
    }

    private fun loadAvatar(url: String?) {
        binding.profilePicture.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_avatar_placeholder)
            error(R.drawable.ic_avatar_placeholder)
            transformations(CircleCropTransformation())
            memoryCacheKey(url)
            diskCacheKey(url)
            listener(
                onError = { _, result ->
                    android.util.Log.e("ProfileActivity", "Avatar load failed: ${result.throwable.message}")
                },
                onSuccess = { _, _ ->
                    android.util.Log.d("ProfileActivity", "Avatar loaded successfully from: $url")
                }
            )
        }
    }

    private fun formatDateForDisplay(date: String?): String {
        if (date.isNullOrEmpty()) return "N/A"

        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate!!)
        } catch (e: Exception) {
            date
        }
    }

    override fun onResume() {
        super.onResume()
        loadValveLimit()
    }
}