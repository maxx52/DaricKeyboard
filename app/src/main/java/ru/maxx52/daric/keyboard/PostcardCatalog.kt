package ru.maxx52.daric.keyboard

internal enum class PostcardCategory(val label: String) {
    ALL("Все"),
    EVERY_DAY("На каждый день"),
    BIRTHDAY("День рождения"),
    WARM_WORDS("Тёплые слова"),
    HOLIDAYS("Праздники")
}

internal data class Postcard(
    val id: String,
    val category: PostcardCategory,
    val title: String,
    val message: String,
    val decoration: String,
    val startColor: Int,
    val endColor: Int,
    val textColor: Int = 0xFFFFFFFF.toInt()
)

internal val postcardCatalog = listOf(
    Postcard(
        id = "good_morning",
        category = PostcardCategory.EVERY_DAY,
        title = "Доброе утро!",
        message = "Пусть новый день принесёт радость и добрые вести",
        decoration = "☀️",
        startColor = 0xFF61C5F4.toInt(),
        endColor = 0xFFFFE68A.toInt(),
        textColor = 0xFF3D3440.toInt()
    ),
    Postcard(
        id = "good_day",
        category = PostcardCategory.EVERY_DAY,
        title = "Хорошего дня!",
        message = "Пусть всё задуманное сегодня обязательно получится",
        decoration = "✨",
        startColor = 0xFF8EC5FC.toInt(),
        endColor = 0xFFE0C3FC.toInt(),
        textColor = 0xFF332C4A.toInt()
    ),
    Postcard(
        id = "good_night",
        category = PostcardCategory.EVERY_DAY,
        title = "Спокойной ночи",
        message = "Пусть сон будет крепким, а утро — светлым",
        decoration = "🌙",
        startColor = 0xFF25395E.toInt(),
        endColor = 0xFF674A7E.toInt()
    ),
    Postcard(
        id = "birthday_cake",
        category = PostcardCategory.BIRTHDAY,
        title = "С днём рождения!",
        message = "Счастья, здоровья и исполнения самых добрых желаний",
        decoration = "🎂",
        startColor = 0xFFF857A6.toInt(),
        endColor = 0xFFFFD66B.toInt()
    ),
    Postcard(
        id = "birthday_party",
        category = PostcardCategory.BIRTHDAY,
        title = "Поздравляю!",
        message = "Пусть впереди ждут яркие события и счастливые моменты",
        decoration = "🎉",
        startColor = 0xFF9D50BB.toInt(),
        endColor = 0xFF6E48AA.toInt()
    ),
    Postcard(
        id = "thank_you",
        category = PostcardCategory.WARM_WORDS,
        title = "Спасибо!",
        message = "За помощь, заботу и тепло, которыми ты делишься",
        decoration = "💜",
        startColor = 0xFF667EEA.toInt(),
        endColor = 0xFF764BA2.toInt()
    ),
    Postcard(
        id = "with_love",
        category = PostcardCategory.WARM_WORDS,
        title = "С любовью",
        message = "Пусть это маленькое послание подарит тебе улыбку",
        decoration = "💗",
        startColor = 0xFFF953C6.toInt(),
        endColor = 0xFFB91D73.toInt()
    ),
    Postcard(
        id = "warm_hug",
        category = PostcardCategory.WARM_WORDS,
        title = "Обнимаю!",
        message = "Пусть рядом всегда будут дорогие и любящие люди",
        decoration = "🤗",
        startColor = 0xFFFF9966.toInt(),
        endColor = 0xFFFF5E62.toInt()
    ),
    Postcard(
        id = "happy_holiday",
        category = PostcardCategory.HOLIDAYS,
        title = "С праздником!",
        message = "Мира, добра, радости и прекрасного настроения",
        decoration = "🌸",
        startColor = 0xFF43C6AC.toInt(),
        endColor = 0xFFF8FFAE.toInt(),
        textColor = 0xFF28483F.toInt()
    )
)
