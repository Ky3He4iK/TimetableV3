package com.ky3he4ik

import java.io.*
import java.net.HttpURLConnection
import java.net.URL

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

internal object IO {
    private fun write(filename: String, text: String, overwrite: Boolean = true) {
        val file = File(filename)
        if (!file.exists())
            while (file.createNewFile());
        if (!file.canWrite())
            throw AccessDeniedException(file)
        if (!overwrite)
            file.writeText(read(filename))
        file.writeText(text)
    }

    private fun read(filename: String) : String{
        val file = File(filename)
        if (!file.exists())
            throw FileNotFoundException("$filename does not exists")
        if (!file.canRead())
            throw AccessDeniedException(file)
        return file.readText()
    }

    fun <T> readJSON(filename: String) : T {
        return Gson().fromJson<T>(read(filename), object : TypeToken<List<T>>() {}.type)
    }

    fun <T> writeJSON(filename: String, t: T, overwrite: Boolean = true) {
        write(filename, GsonBuilder().setPrettyPrinting().create().toJson(t), overwrite)
    }

    fun get(url: URL, fast: Boolean = false) : String {
        with(url.openConnection() as HttpURLConnection) {
            setRequestProperty("User-agent", "Timetable bot")
            // optional default is GET
            requestMethod = "GET"


            BufferedReader(InputStreamReader(inputStream, "windows-1251")).use {
                if (responseCode != 200)
                    throw IOException("Response code is $responseCode ($responseMessage), not 200 on URL $url")
                val response = StringBuffer()
                var inputLine = it.readLine()
                Charsets
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                Thread.sleep(if (fast) 10 else 1000)
                return response.toString()
            }
        }
    }

    fun get(url: URL, parameters: Map <String, String>, fast: Boolean = false) : String {
        val parametersString = StringBuilder()
        for (it in parameters)
            parametersString.append(it.key).append('=').append(it.value).append("&")
        return get(URL(url.toString() + '?' + parametersString.substring(0, parametersString.length - 1)), fast)
    }
}
