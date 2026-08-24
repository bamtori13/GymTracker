package com.gymtracker.app.ui.util

/** 한글 음절의 초성 19자. 유니코드 가(0xAC00)부터 초성이 588자마다 하나씩 바뀐다. */
private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
private const val HANGUL_FIRST = 0xAC00
private const val HANGUL_LAST = 0xD7A3
private const val CHOSEONG_BLOCK = 588

/** "벤치프레스" -> "ㅂㅊㅍㄹㅅ". 한글이 아닌 글자는 그대로 둔다. */
fun choseongOf(text: String): String = buildString {
    text.forEach { ch ->
        val code = ch.code
        if (code in HANGUL_FIRST..HANGUL_LAST) {
            append(CHOSEONG[(code - HANGUL_FIRST) / CHOSEONG_BLOCK])
        } else {
            append(ch)
        }
    }
}

/**
 * 검색 매칭. 일반 부분일치와 초성 부분일치를 함께 본다.
 * 초성 비교는 질의가 초성만으로 이루어진 경우에만 의미가 있으므로,
 * 질의를 그대로 초성 문자열에 대고 찾는다 ("ㅂㅊ" -> "ㅂㅊㅍㄹㅅ" 안에 있음).
 */
fun matchesSearch(text: String, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    if (text.contains(q, ignoreCase = true)) return true
    return choseongOf(text).contains(q)
}
