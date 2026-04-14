package com.megamini.android

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var linkEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val linearLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        linkEditText = EditText(this).apply {
            hint = "Вставьте ссылку Mega.nz"
            setText("https://mega.nz/folder/v2YShZDS#c5yJYxDStBVYUQmnLjc6Xg")
        }

        downloadButton = Button(this).apply {
            text = "Скачать первый файл"
            setOnClickListener {
                val link = linkEditText.text.toString()
                if (link.isNotBlank()) {
                    startDownload(link)
                } else {
                    Toast.makeText(this@MainActivity, "Введите ссылку", Toast.LENGTH_SHORT).show()
                }
            }
        }

        statusTextView = TextView(this).apply {
            text = "Ожидание..."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        linearLayout.addView(linkEditText)
        linearLayout.addView(downloadButton)
        linearLayout.addView(statusTextView)

        setContentView(linearLayout)
    }

    private fun startDownload(link: String) {
        statusTextView.text = "Получение списка файлов..."

        lifecycleScope.launch {
            try {
                val megaFiles = withContext(Dispatchers.IO) {
                    MegaMini.getNodesFromLink(link)
                }

                if (megaFiles.isNullOrEmpty()) {
                    statusTextView.text = "Файлы не найдены"
                    Toast.makeText(this@MainActivity, "Файлы не найдены", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val megaFile = megaFiles.first()
                statusTextView.text = "Файл найден: ${megaFile.name ?: megaFile.id}"

                val outputFileName = megaFile.name?.takeIf { it.isNotBlank() } ?: "download.bin"
                val outputFile = File(getExternalFilesDir(null), outputFileName)

                withContext(Dispatchers.IO) {
                    MegaMini.download(megaFile)?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Не удалось открыть поток для скачивания")
                }

                statusTextView.text = "Скачано в: ${outputFile.absolutePath}"
                Toast.makeText(this@MainActivity, "Файл скачан успешно!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                statusTextView.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
