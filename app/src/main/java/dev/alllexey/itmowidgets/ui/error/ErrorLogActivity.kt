package dev.alllexey.itmowidgets.ui.error

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.repository.ErrorLogEntry
import dev.alllexey.itmowidgets.databinding.ActivityErrorLogBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ErrorLogActivity : AppCompatActivity(), ErrorLogListener {

    private lateinit var binding: ActivityErrorLogBinding
    private lateinit var errorLogAdapter: ErrorLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityErrorLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = systemBars.left, top = systemBars.top, right = systemBars.right)
            insets
        }

        setupRecyclerView()
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            appContainer.errorLogRepository.data.collect {
                errorLogAdapter.submitList(it.logs)
            }
        }
    }

    private fun setupRecyclerView() {
        errorLogAdapter = ErrorLogAdapter(this)
        binding.errorLogs.apply {
            adapter = errorLogAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onItemClick(entry: ErrorLogEntry) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_error_log, null)
        val errorStacktrace = dialogView.findViewById<TextView>(R.id.error_log_view)
        errorStacktrace.text = entry.stacktrace

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton("Скопировать") { _, _ ->
                val clipboard: ClipboardManager =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("stacktrace", entry.stacktrace)
                clipboard.setPrimaryClip(clip)
            }
            .setPositiveButton("Назад") { _, _ ->
            }
            .show()
    }
}