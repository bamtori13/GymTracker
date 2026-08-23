package com.gymtracker.app.data.local

import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseInputType

/** 앱 최초 실행 시 exercise 테이블이 비어 있으면 한 번 넣어주는 기본 제공 목록. */
object DefaultExercises {

    val ALL: List<Exercise> = listOf(
        // 가슴
        Exercise(name = "벤치프레스", bodyPart = "가슴"),
        Exercise(name = "인클라인 벤치프레스", bodyPart = "가슴"),
        Exercise(name = "딥스", bodyPart = "가슴"),
        Exercise(name = "체스트프레스머신", bodyPart = "가슴"),
        // 등
        Exercise(name = "랫풀다운", bodyPart = "등"),
        Exercise(name = "시티드로우", bodyPart = "등"),
        Exercise(name = "데드리프트", bodyPart = "등"),
        Exercise(name = "풀업", bodyPart = "등"),
        Exercise(name = "바벨로우", bodyPart = "등"),
        // 어깨
        Exercise(name = "숄더프레스", bodyPart = "어깨"),
        Exercise(name = "사이드레터럴레이즈", bodyPart = "어깨"),
        Exercise(name = "프론트레이즈", bodyPart = "어깨"),
        Exercise(name = "페이스풀", bodyPart = "어깨"),
        // 하체
        Exercise(name = "스쿼트", bodyPart = "하체"),
        Exercise(name = "레그프레스", bodyPart = "하체"),
        Exercise(name = "레그컬", bodyPart = "하체"),
        Exercise(name = "레그익스텐션", bodyPart = "하체"),
        Exercise(name = "런지", bodyPart = "하체"),
        // 팔
        Exercise(name = "바벨컬", bodyPart = "팔"),
        Exercise(name = "덤벨컬", bodyPart = "팔"),
        Exercise(name = "트라이셉스푸시다운", bodyPart = "팔"),
        Exercise(name = "케이블컬", bodyPart = "팔"),
        // 코어 (일부는 시간 기반)
        Exercise(name = "크런치", bodyPart = "코어"),
        Exercise(name = "행잉레그레이즈", bodyPart = "코어"),
        Exercise(name = "플랭크", bodyPart = "코어", inputType = ExerciseInputType.TIME),
        // 유산소 (시간 기반)
        Exercise(name = "러닝머신", bodyPart = "유산소", inputType = ExerciseInputType.TIME),
        Exercise(name = "사이클", bodyPart = "유산소", inputType = ExerciseInputType.TIME)
    )

    /** 목록/칩 순서 및 필터용 부위 전체 집합. "전체" 칩은 화면에서 별도로 앞에 붙인다. */
    val BODY_PARTS: List<String> = listOf("가슴", "등", "어깨", "하체", "팔", "코어", "유산소")
}
