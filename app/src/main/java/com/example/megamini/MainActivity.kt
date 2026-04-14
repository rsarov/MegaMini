package com.example.megamini

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.megamini.mega.MegaMiniClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val sampleLink = "https://mega.nz/folder/v2YShZDS#c5yJYxDStBVYUQmnLjc6Xg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.downloadButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        button.setOnClickListener {
            lifecycleScope.launch {
                statusText.text = "Loading nodes..."

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val files = MegaMiniClient.getNodesFromLink(sampleLink)
                        val first = files.firstOrNull() ?: error("No files in shared folder")
                        val output = File(cacheDir, (first.name ?: "download") + ".bin")
                        MegaMiniClient.download(first).use { input ->
                            output.outputStream().use { out ->
                                input.copyTo(out)
                            }
                        }
                        output
                    }
                }

                statusText.text = result.fold(
                    onSuccess = { "Downloaded to: ${it.absolutePath}" },
                    onFailure = { "Error: ${it.message}" }
                )
            }
        }
    }
}
