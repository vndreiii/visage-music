package com.music.vivi.compose.ui.player.lyrics

/** A translation target: Google Translate language code, English name, and the name in the language itself. */
data class TranslateLanguage(val code: String, val english: String, val native: String) {
    /** "Japanese · 日本語", or just the English name when both are the same. */
    val display: String get() = if (native.equals(english, ignoreCase = true)) english else "$english · $native"
}

object LyricsLanguages {
    /** Shown first in the picker, in this order. */
    val commonCodes = listOf("en", "es", "fr", "de", "pt", "it", "ru", "ja", "ko", "zh-CN", "zh-TW", "ar", "hi", "id", "tr", "nl", "pl", "vi", "tl")

    /** The languages Google Translate supports (codes as it uses them; stored codes such as `en` and `zh-CN` are unchanged). */
    val all: List<TranslateLanguage> = listOf(
        "af" to ("Afrikaans" to "Afrikaans"), "sq" to ("Albanian" to "Shqip"), "am" to ("Amharic" to "አማርኛ"),
        "ar" to ("Arabic" to "العربية"), "hy" to ("Armenian" to "Հայերեն"), "as" to ("Assamese" to "অসমীয়া"),
        "ay" to ("Aymara" to "Aymar aru"), "az" to ("Azerbaijani" to "Azərbaycan"), "bm" to ("Bambara" to "Bamanankan"),
        "eu" to ("Basque" to "Euskara"), "be" to ("Belarusian" to "Беларуская"), "bn" to ("Bengali" to "বাংলা"),
        "bho" to ("Bhojpuri" to "भोजपुरी"), "bs" to ("Bosnian" to "Bosanski"), "bg" to ("Bulgarian" to "Български"),
        "ca" to ("Catalan" to "Català"), "ceb" to ("Cebuano" to "Cebuano"), "ny" to ("Chichewa" to "Chichewa"),
        "zh-CN" to ("Chinese (Simplified)" to "简体中文"), "zh-TW" to ("Chinese (Traditional)" to "繁體中文"),
        "co" to ("Corsican" to "Corsu"), "hr" to ("Croatian" to "Hrvatski"), "cs" to ("Czech" to "Čeština"),
        "da" to ("Danish" to "Dansk"), "dv" to ("Dhivehi" to "ދިވެހި"), "doi" to ("Dogri" to "डोगरी"),
        "nl" to ("Dutch" to "Nederlands"), "en" to ("English" to "English"), "eo" to ("Esperanto" to "Esperanto"),
        "et" to ("Estonian" to "Eesti"), "ee" to ("Ewe" to "Eʋegbe"), "tl" to ("Filipino" to "Filipino"),
        "fi" to ("Finnish" to "Suomi"), "fr" to ("French" to "Français"), "fy" to ("Frisian" to "Frysk"),
        "gl" to ("Galician" to "Galego"), "ka" to ("Georgian" to "ქართული"), "de" to ("German" to "Deutsch"),
        "el" to ("Greek" to "Ελληνικά"), "gn" to ("Guarani" to "Avañe'ẽ"), "gu" to ("Gujarati" to "ગુજરાતી"),
        "ht" to ("Haitian Creole" to "Kreyòl ayisyen"), "ha" to ("Hausa" to "Hausa"), "haw" to ("Hawaiian" to "ʻŌlelo Hawaiʻi"),
        "he" to ("Hebrew" to "עברית"), "hi" to ("Hindi" to "हिन्दी"), "hmn" to ("Hmong" to "Hmoob"),
        "hu" to ("Hungarian" to "Magyar"), "is" to ("Icelandic" to "Íslenska"), "ig" to ("Igbo" to "Igbo"),
        "ilo" to ("Ilocano" to "Ilokano"), "id" to ("Indonesian" to "Bahasa Indonesia"), "ga" to ("Irish" to "Gaeilge"),
        "it" to ("Italian" to "Italiano"), "ja" to ("Japanese" to "日本語"), "jw" to ("Javanese" to "Basa Jawa"),
        "kn" to ("Kannada" to "ಕನ್ನಡ"), "kk" to ("Kazakh" to "Қазақ тілі"), "km" to ("Khmer" to "ខ្មែរ"),
        "rw" to ("Kinyarwanda" to "Kinyarwanda"), "gom" to ("Konkani" to "कोंकणी"), "ko" to ("Korean" to "한국어"),
        "kri" to ("Krio" to "Krio"), "ku" to ("Kurdish (Kurmanji)" to "Kurdî"), "ckb" to ("Kurdish (Sorani)" to "کوردی"),
        "ky" to ("Kyrgyz" to "Кыргызча"), "lo" to ("Lao" to "ລາວ"), "la" to ("Latin" to "Latina"),
        "lv" to ("Latvian" to "Latviešu"), "ln" to ("Lingala" to "Lingála"), "lt" to ("Lithuanian" to "Lietuvių"),
        "lg" to ("Luganda" to "Luganda"), "lb" to ("Luxembourgish" to "Lëtzebuergesch"), "mk" to ("Macedonian" to "Македонски"),
        "mai" to ("Maithili" to "मैथिली"), "mg" to ("Malagasy" to "Malagasy"), "ms" to ("Malay" to "Bahasa Melayu"),
        "ml" to ("Malayalam" to "മലയാളം"), "mt" to ("Maltese" to "Malti"), "mi" to ("Maori" to "Te Reo Māori"),
        "mr" to ("Marathi" to "मराठी"), "mni-Mtei" to ("Meiteilon (Manipuri)" to "ꯃꯤꯇꯩꯂꯣꯟ"), "lus" to ("Mizo" to "Mizo ṭawng"),
        "mn" to ("Mongolian" to "Монгол"), "my" to ("Myanmar (Burmese)" to "မြန်မာ"), "ne" to ("Nepali" to "नेपाली"),
        "no" to ("Norwegian" to "Norsk"), "or" to ("Odia (Oriya)" to "ଓଡ଼ିଆ"), "om" to ("Oromo" to "Afaan Oromoo"),
        "ps" to ("Pashto" to "پښتو"), "fa" to ("Persian" to "فارسی"), "pl" to ("Polish" to "Polski"),
        "pt" to ("Portuguese" to "Português"), "pa" to ("Punjabi" to "ਪੰਜਾਬੀ"), "qu" to ("Quechua" to "Runasimi"),
        "ro" to ("Romanian" to "Română"), "ru" to ("Russian" to "Русский"), "sm" to ("Samoan" to "Gagana Samoa"),
        "sa" to ("Sanskrit" to "संस्कृतम्"), "gd" to ("Scots Gaelic" to "Gàidhlig"), "nso" to ("Sepedi" to "Sepedi"),
        "sr" to ("Serbian" to "Српски"), "st" to ("Sesotho" to "Sesotho"), "sn" to ("Shona" to "chiShona"),
        "sd" to ("Sindhi" to "سنڌي"), "si" to ("Sinhala" to "සිංහල"), "sk" to ("Slovak" to "Slovenčina"),
        "sl" to ("Slovenian" to "Slovenščina"), "so" to ("Somali" to "Soomaali"), "es" to ("Spanish" to "Español"),
        "su" to ("Sundanese" to "Basa Sunda"), "sw" to ("Swahili" to "Kiswahili"), "sv" to ("Swedish" to "Svenska"),
        "tg" to ("Tajik" to "Тоҷикӣ"), "ta" to ("Tamil" to "தமிழ்"), "tt" to ("Tatar" to "Татар"),
        "te" to ("Telugu" to "తెలుగు"), "th" to ("Thai" to "ไทย"), "ti" to ("Tigrinya" to "ትግርኛ"),
        "ts" to ("Tsonga" to "Xitsonga"), "tr" to ("Turkish" to "Türkçe"), "tk" to ("Turkmen" to "Türkmen"),
        "ak" to ("Twi (Akan)" to "Twi"), "uk" to ("Ukrainian" to "Українська"), "ur" to ("Urdu" to "اردو"),
        "ug" to ("Uyghur" to "ئۇيغۇرچە"), "uz" to ("Uzbek" to "Oʻzbek"), "vi" to ("Vietnamese" to "Tiếng Việt"),
        "cy" to ("Welsh" to "Cymraeg"), "xh" to ("Xhosa" to "isiXhosa"), "yi" to ("Yiddish" to "ייִדיש"),
        "yo" to ("Yoruba" to "Yorùbá"), "zu" to ("Zulu" to "isiZulu"),
    ).map { (code, names) -> TranslateLanguage(code, names.first, names.second) }

    fun byCode(code: String): TranslateLanguage? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    /** The common languages in [commonCodes] order. */
    val common: List<TranslateLanguage> = commonCodes.mapNotNull { byCode(it) }
}
