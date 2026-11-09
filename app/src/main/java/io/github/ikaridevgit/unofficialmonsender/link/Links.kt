package io.github.ikaridevgit.unofficialmonsender.link

import java.net.URI

object Links {
    private val URL_PATTERN = Regex("""https?://[^\s<>"'`{}|\\^]+""", RegexOption.IGNORE_CASE)
    private val IMAGE_EXT = Regex("""\.(jpe?g|png|gif|webp|avif|jxl)$""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?'\""

    /** First http(s) link in [text], without punctuation that only ends the surrounding sentence. */
    fun firstLink(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val candidate = URL_PATTERN.find(text)?.value ?: return null
        return trimTrailing(candidate).takeIf { hostOf(it) != null }
    }

    fun hostOf(url: String): String? = runCatching {
        URI(url).host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Only what monbooru ingests counts as a direct file, matching MonSender. */
    fun isImageUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            IMAGE_EXT.containsMatchIn(uri.path.orEmpty())
    }.getOrDefault(false)

    fun normalizeServerUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "http://$url"
        url = url.trimEnd('/')
        return url.takeIf { runCatching { !URI(it).host.isNullOrEmpty() }.getOrDefault(false) }
    }

    /** Turns user input such as "https://www.Example.com/path" into a host rule ("example.com"). */
    fun normalizeHostRule(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val host = hostOf(withScheme) ?: return null
        return host.takeIf { '.' in it && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    fun matchesHostRule(host: String, rule: String): Boolean = host == rule || host.endsWith(".$rule")

    private fun trimTrailing(url: String): String {
        var s = url
        while (s.isNotEmpty()) {
            val c = s.last()
            val unbalanced = (c == ')' && s.count { it == '(' } < s.count { it == ')' }) ||
                (c == ']' && s.count { it == '[' } < s.count { it == ']' })
            if (c in TRAILING || unbalanced) s = s.dropLast(1) else break
        }
        return s
    }
}
