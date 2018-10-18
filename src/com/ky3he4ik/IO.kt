package com.ky3he4ik

import com.google.gson.Gson
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

internal object IO {
    private fun checkFile(file: File, create: Boolean = false) {
        if (!file.exists()) {
            if (create)
                file.createNewFile()
            else
                throw AccessDeniedException(file)
        }
        if (!file.canWrite())
            throw AccessDeniedException(file)
    }

    private fun write(filename: String, text: String, overwrite: Boolean = true) {
        val directories = filename.split(File.separator)
        directories.dropLast(1).forEach {
            val file = File(it)
            if (!file.exists())
                file.mkdir()
        }

        val file = File(filename)
        checkFile(file, true)
        val writer = FileOutputStream(file, !overwrite).bufferedWriter()
        writer.write(text)
        writer.flush()
        writer.close()
    }

    fun read(filename: String) : String {
        val file = File(filename)
        checkFile(file)
        return file.readText()
    }

    fun <T> readJSON(filename: String) : T =
            Gson().fromJson<T>(read(filename), object : TypeToken<T>() {}.type)

    inline fun <reified T> readJSONArray(filename: String) : T =
            Gson().fromJson<T>(read(filename), object : TypeToken<T>() {}.type)

    fun <T> writeJSON(filename: String, t: T, overwrite: Boolean = true) =
            write(filename, GsonBuilder().setPrettyPrinting().create().toJson(t), overwrite)

    fun get(url: URL, fast: Boolean = false) : String {
        with(url.openConnection() as HttpURLConnection) {
            setRequestProperty("User-agent", BotConfig.userAgent)
            requestMethod = "GET"
            BufferedReader(InputStreamReader(inputStream, "windows-1251")).use {
                if (responseCode != 200) {
                    LOG.e("IO/get", "GET $url: $responseCode\n${it.readText()}")
                    throw IOException("Response code is $responseCode ($responseMessage), not 200 on URL $url")
                }

                // Big pause to reduce server's load
                Thread.sleep(if (fast) 100 else 1000)
                return it.readText()
            }
        }
    }

    fun get(url: URL, parameters: Map <Any, Any>, fast: Boolean = false) : String {
        val parametersString = StringBuilder()
        for (it in parameters)
            parametersString.append("${it.key}=${it.value}&")
        return get(URL(url.toString() + '?' + parametersString.dropLast(1).toString()), fast)
    }
}
