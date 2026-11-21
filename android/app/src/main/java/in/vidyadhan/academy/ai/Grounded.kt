package `in`.vidyadhan.academy.ai

/** One web page the answer was based on. */
data class Source(val title: String, val domain: String, val uri: String)

/** A piece of the answer text and the sources (indices into [Grounded.sources]) that back it. */
data class Support(val text: String, val sources: List<Int>)

/** The answer text, the pages it cites, and which piece of text came from which pages. */
data class Grounded(val text: String, val sources: List<Source>, val supports: List<Support>)
