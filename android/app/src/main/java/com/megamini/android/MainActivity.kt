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

        // Создаем простой UI программно
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

                if (megaFiles != null && megaFiles.isNotEmpty()) {
                    statusTextView.text = "Файл найден: ${megaFiles[0].name}"
                    
                    val file = File(getExternalFilesDir(null), "${megaFiles[0].name}.zip")
                    FileOutputStream(file).use { outputStream ->
                        withContext(Dispatchers.IO) {
                            val stream = MegaMini.download(megaFiles[0])
                            stream?.use { input ->
                                input.copyTo(outputStream)
                            }
                        }
                    }
                    
                    statusTextView.text = "Скачано в: ${file.absolutePath}"
                    Toast.makeText(this@MainActivity, "Файл скачан успешно!", Toast.LENGTH_LONG).show()
                } else {
                    statusTextView.text = "Файлы не найдены"
                    Toast.makeText(this@MainActivity, "Файлы не найдены", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                statusTextView.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}
