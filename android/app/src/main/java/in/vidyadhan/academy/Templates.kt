package `in`.vidyadhan.academy

/** Message wording the owner can change in Settings. Words in {curly brackets} are filled in automatically. */
object Templates {
    data class Template(val key: String, val title: String, val mr: String, val en: String, val fields: String)

    val all = listOf(
        Template("fee", "Fee reminder",
            "नमस्कार {name}, {academy} कडून आठवण: तुमचा {amount} चा हप्ता {date} रोजी देय आहे. कृपया वेळेत भरावा. धन्यवाद.",
            "Hello {name}, a reminder from {academy}: your installment of {amount} is due on {date}. Please pay on time. Thank you.",
            "{name} {amount} {date} {academy}"),
        Template("library", "Abhyasika renewal",
            "नमस्कार {name}, {academy} अभ्यासिका: तुमची सीट {seat} ची मासिक फी {amount} {date} रोजी देय आहे. धन्यवाद.",
            "Hello {name}, {academy} Abhyasika: the monthly fee of {amount} for seat {seat} is due on {date}. Thank you.",
            "{name} {seat} {amount} {date} {academy}"),
        Template("enquiry", "Enquiry thank-you",
            "नमस्कार {name}, {academy} मध्ये चौकशी केल्याबद्दल धन्यवाद. अधिक माहितीसाठी संपर्क: {phone}",
            "Hello {name}, thank you for your enquiry at {academy}. For details, call {phone}.",
            "{name} {academy} {phone}"),
        Template("birthday", "Birthday wish",
            "🎉 वाढदिवसाच्या हार्दिक शुभेच्छा, {name}! तुमची अभ्यासातील मेहनत फळाला येवो आणि यश तुमच्या पायाशी असो. — {academy}",
            "🎉 Happy birthday, {name}! May your hard work bring you success. — {academy}",
            "{name} {academy}"),
    )

    fun text(key: String, marathi: Boolean): String {
        val t = all.first { it.key == key }
        return AppSettings.template(key, marathi) ?: if (marathi) t.mr else t.en
    }

    /** The bot's message: Marathi and English together, safe for Telegram. */
    fun bot(key: String, vars: Map<String, String>): String {
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        return esc(fill(key, true, vars)) + "\n\n" + esc(fill(key, false, vars))
    }

    /** Sample values, for tests and previews. */
    val sample = mapOf("name" to "Rohan", "amount" to "₹9,000", "date" to "22 Nov", "course" to "MPSC", "seat" to "14 + locker")

    fun fill(key: String, marathi: Boolean, vars: Map<String, String>): String {
        var s = text(key, marathi)
        val base = mapOf("academy" to (if (marathi) "विद्याधन अकॅडमी" else AppSettings.academyName), "phone" to AppSettings.academyPhone)
        (base + vars).forEach { (k, v) -> s = s.replace("{$k}", v) }
        return s
    }
}
