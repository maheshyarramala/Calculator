fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
  return try {
    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
      stream.write(content.toByteArray(Charsets.UTF_8))
      stream.flush()
    }
    true
  } catch (e: Exception) {
    false
  }
}

fun readFromUri(context: Context, uri: Uri): String? {
  return try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
      BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        reader.readText()
      }
    }
  } catch (e: Exception) {
    null
  }
}