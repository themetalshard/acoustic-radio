package com.metalshard.projectwave

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter


object M3UExporter {
    fun export(context: Context, uri: Uri, stations: List<RadioStation>) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                writer.write("#EXTM3U\n")
                stations.forEach { station ->
                    writer.write("#EXTINF:-1 tvg-logo=\"${station.imageUrl}\" group-title=\"radio\" radio=\"true\", ${station.name}\n")
                    writer.write("${station.streamUrl}\n")
                }
            }
        }
    }
}
object M3UParser {
    fun parse(context: Context, uri: Uri): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        val content = context.contentResolver.openInputStream(uri) ?: return emptyList()

        val reader = BufferedReader(InputStreamReader(content))
        var currentName = ""
        var currentLogo = ""

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXTINF") -> {
                        currentName = trimmed.substringAfterLast(",").trim()

                        currentLogo = if (trimmed.contains("tvg-logo=\"")) {
                            trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                        } else {
                            ""
                        }
                    }

                    trimmed.startsWith("http") -> {
                        if (currentName.isEmpty()) currentName = "Unknown Station"

                        stations.add(
                            RadioStation(
                                id = (System.currentTimeMillis() + stations.size).toInt(),
                                name = currentName,
                                streamUrl = trimmed,
                                imageUrl = currentLogo.ifEmpty { "" }
                            )
                        )
                        currentName = ""
                        currentLogo = ""
                    }
                }
            }
        }
        return stations
    }
}

