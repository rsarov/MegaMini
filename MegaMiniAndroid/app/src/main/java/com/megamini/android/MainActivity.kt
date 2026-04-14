package com.megamini.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val link = "https://mega.nz/folder/v2YShZDS#c5yJYxDStBVYUQmnLjc6Xg"

        executor.execute {
            try {
                val files = MegaMiniClient.getNodesFromLink(link)
                if (files.isEmpty()) {
                    runOnUiThread { statusText.text = "No files found" }
                    return@execute
                }

                val firstFile = files.first()
                val data = MegaMiniClient.download(firstFile)
                val outFile = File(filesDir, "0.zip")
                outFile.writeBytes(data)

                runOnUiThread {
                    statusText.text = "Downloaded: ${firstFile.name}\nSaved to: ${outFile.absolutePath}\nSize: ${data.size} bytes"
                }
            } catch (t: Throwable) {
                runOnUiThread { statusText.text = "Error: ${t.message}" }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
