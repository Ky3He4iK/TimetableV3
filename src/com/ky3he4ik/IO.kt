package com.ky3he4ik

import com.google.gson.Gson
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

internal object IO {
    private fun write(filename: String, text: String, overwrite: Boolean = true) {
        val directories = filename.split(File.separator)
        directories.dropLast(1).forEach { val file = File(it)
            if (!file.exists())
                file.mkdir()
        }

        val file = File(filename)
        if (!file.exists())
            file.createNewFile()
        if (!file.canWrite())
            throw AccessDeniedException(file)
        val writer = OutputStreamWriter(FileOutputStream(file, !overwrite)).buffered()
        writer.write(text)
        writer.flush()
        writer.close()
    }

    fun read(filename: String) : String {
        val file = File(filename)
        if (!file.exists())
            throw FileNotFoundException("$filename does not exists")
        if (!file.canRead())
            throw AccessDeniedException(file)
        return file.readText()
    }

    fun <T> readJSON(filename: String) : T {
        return Gson().fromJson<T>(read(filename), object : TypeToken<T>() {}.type)
    }

    inline fun <reified T> readJSONArray(filename: String) : T {
        return Gson().fromJson<T>(read(filename), object : TypeToken<T>() {}.type)
    }
    // Read by Klaxon, write by Gson. 2 Libraries for JSON isn't enough, need to add third :)
    fun <T> writeJSON(filename: String, t: T, overwrite: Boolean = true) {
        write(filename, GsonBuilder().setPrettyPrinting().create().toJson(t), overwrite)
    }

    fun get(url: URL, fast: Boolean = false) : String {
        with(url.openConnection() as HttpURLConnection) {
            setRequestProperty("User-agent", "Bot")
            //Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:61.0) Gecko/20100101 Firefox/61.0
            // optional default is GET
            requestMethod = "GET"
            BufferedReader(InputStreamReader(inputStream, "windows-1251")).use {
                if (responseCode != 200)
                    throw IOException("Response code is $responseCode ($responseMessage), not 200 on URL $url")
                val response = StringBuffer()
                var inputLine = it.readLine()

                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                Thread.sleep(if (fast) 100 else 1000) //TODO: reduce
                return response.toString()
            }
        }
    }

    fun get(url: URL, parameters: Map <String, String>, fast: Boolean = false) : String {
        val parametersString = StringBuilder()
        for (it in parameters)
            parametersString.append(it.key).append('=').append(it.value).append("&")
        return get(URL(url.toString() + '?' + parametersString.dropLast(1).toString()), fast)
    }

    fun exists(filename: String): Boolean = File(filename).exists()
}
