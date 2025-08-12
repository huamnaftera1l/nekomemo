package com.example.nekomemo

data class WordDefinition(
    val word: String,
    val partOfSpeech: String,
    val translation: String,
    val contextMeaning: String? = null
)

data class QuizQuestion(
    val word: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val correctTranslation: String
)

data class QuizResult(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val percentage: Double
) {
    val evaluation: String
        get() = when {
            percentage >= 90 -> "🏆 优秀！猫很崇拜你！"
            percentage >= 70 -> "👍 不错！你的努力猫猫都看在眼里！"
            else -> "What can I say? 猫猫 OUT!😭"
        }
}