# GymTracker (Phase 1)

기존 Expo/React Native 앱(FitnessTracker)과는 별개로, 요구사항에 맞춰 **Native Android(Kotlin + Jetpack Compose + Room)** 로 새로 시작한 프로젝트입니다.

## 1. 이번 Phase에서 구현한 것 (Phase 1)

문서의 개발 순서를 그대로 따랐습니다.

- 루틴 생성
- 운동 추가 (세트 수 / 반복수 범위 / 증량 단위 / 시작 중량 설정)
- 운동 시작 (세션 생성)
- 세트 기록 (지난번 기록 자동 표시, 중량/반복수 입력)
- 운동 종료 (요약: 총 세트 수, 총 볼륨)
- Room 저장 (전부 로컬, 서버/로그인 없음)

**Phase 2(다음 중량 자동 추천, PR 자동 감지), Phase 3(History 그래프)는 아직 구현하지 않았습니다.**
지금은 사용자가 직접 시작 중량과 목표를 입력하고, 다음 세션에서는 "지난번 값"만 자동으로 보여줍니다.

## 2. 프로젝트 구조

```
app/src/main/java/com/gymtracker/app/
├── MainActivity.kt                # Navigation 그래프 (Home → RoutineDetail → Workout → Summary)
├── data/
│   ├── local/
│   │   ├── entity/                # Room Entity 5종 (아래 데이터 모델 참고)
│   │   ├── dao/                   # Entity별 DAO
│   │   ├── AppDatabase.kt         # Room DB, 싱글턴
│   │   └── Converters.kt          # enum(PersonalRecordType) 저장용 TypeConverter
│   └── repository/
│       └── WorkoutRepository.kt   # ViewModel이 유일하게 의존하는 계층. DAO를 직접 노출하지 않음
└── ui/
    ├── ViewModelFactory.kt        # Repository를 ViewModel에 주입 (Hilt 없이 최소 구현)
    ├── home/HomeScreen.kt         # 루틴 목록 + 루틴 생성
    ├── routine/
    │   ├── RoutineViewModel.kt
    │   └── RoutineDetailScreen.kt # 운동 목록 + 운동 추가 + "운동 시작" 버튼
    ├── workout/
    │   ├── WorkoutViewModel.kt    # 세션 생성, 세트 기록, 운동 전환, 운동 종료
    │   └── WorkoutScreen.kt       # 운동 중 핵심 화면 (세트 입력)
    └── summary/SummaryScreen.kt   # 운동 종료 요약
```

### 왜 이렇게 설계했는지

- **Repository 계층 분리**: `ViewModel`은 `AppDatabase`나 개별 `Dao`를 절대 직접 참조하지 않고 `WorkoutRepository`만 사용합니다. 나중에 백업/동기화 기능을 추가할 때 이 계층만 확장하면 되도록 하기 위함입니다 (문서 14번 요구사항).
- **DI 없이 최소 Factory**: 프로젝트 초기 단계에서 Hilt 등 DI 프레임워크를 도입하면 설정 비용이 커서, Phase 1에서는 `ViewModelFactory` 하나로 단순하게 처리했습니다. 나중에 필요하면 Hilt로 교체 가능한 구조입니다.
- **세트 기록은 Upsert**: 같은 세트를 여러 번 수정해도(예: reps를 고쳐 입력) 새로운 행이 쌓이지 않도록 `ExerciseSetDao.upsert()`를 사용했습니다.
- **"지난번" 조회**: 매 운동마다 "가장 최근에 종료된 세션"을 찾아 같은 운동의 세트를 가져오는 방식으로 구현했습니다. 세션에 `endedAt`이 있는 것만 "완료된 세션"으로 간주합니다.

## 3. 데이터 모델

```
WorkoutRoutine (루틴)
 └─ Exercise (루틴에 속한 운동, 목표 세트/반복수/증량 단위 보관)
      └─ ExerciseSet (세션 중 특정 운동의 한 세트 기록)

WorkoutSession (루틴을 한 번 수행한 기록, startedAt/endedAt)
      └─ ExerciseSet (sessionId + exerciseId로 연결)

PersonalRecord (Phase 2에서 채워질 PR 이력 테이블. 지금은 스키마만 존재)
```

- `Exercise.currentTargetWeight`는 Phase 1에서는 사용자가 직접 입력한 값 그대로 유지되고, Phase 2에서 Double Progression 알고리즘이 자동으로 갱신하게 됩니다.
- 모든 외래키는 `CASCADE` 삭제로 설정되어 루틴을 지우면 하위 데이터가 함께 정리됩니다.

## 4. 실행 방법

1. Android Studio (Koala 이상 권장)에서 `GymTracker` 폴더를 **Open**으로 엽니다.
2. Gradle Sync가 끝나면 `app` 모듈을 에뮬레이터 또는 실기기(minSdk 26 이상)에서 Run 합니다.
3. 최초 실행 시 DB는 비어 있으므로 홈 화면에서 `+` 버튼으로 루틴을 만드는 것부터 시작합니다.

> 이 환경(샌드박스)에는 Android SDK가 없어 실제 컴파일/APK 빌드는 하지 못했습니다. Android Studio에서 여는 것을 전제로 코드를 작성했습니다. Sync/빌드 중 에러가 나면 알려주시면 바로 고치겠습니다.

## 5. 테스트 방법 (수동 시나리오)

1. **루틴 생성 → 운동 추가**
   - 홈에서 "하체" 루틴 생성 → 상세 화면에서 Squat(4세트, 8~10회, 60kg 시작) 추가.
2. **첫 운동 시작**
   - "운동 시작" 클릭 → Squat 화면 진입 → "지난 기록 없음" 문구 확인 → 4세트 모두 60kg×10으로 완료 체크.
   - 마지막 운동이므로 "운동 종료" 클릭 → 요약 화면에 4 Sets / 총 볼륨 2400kg 표시 확인.
3. **두 번째 운동 시작**
   - 같은 루틴으로 다시 "운동 시작" → Squat 화면에서 "지난번 60.0×10 60.0×10 60.0×10 60.0×10"이 표시되는지 확인.
   - 세트 입력 필드의 기본값이 지난번 값으로 자동 채워져 있는지 확인.
4. **여러 운동 루틴**
   - 운동을 2개 이상 추가한 루틴에서 "다음 운동" 버튼으로 정상적으로 다음 운동으로 넘어가는지 확인.

## 6. 다음 단계 제안

- **Phase 2**: `Exercise.currentTargetWeight`를 세션 종료 시점에 Double Progression 규칙으로 자동 갱신하고, `PersonalRecord`를 실제로 기록/표시.
- **Phase 3**: 운동별 History 화면 + 중량 변화 그래프(Canvas 또는 라이브러리).
- **Phase 4**: 입력 흐름 최적화(숫자 키패드, 스와이프로 세트 완료 등), 애니메이션 최소화.

---

## 7. 첫 화면(오늘) 추가 — 구조 변경 안내

요청하신 목업(날짜별로 여러 운동을 카드로 펼쳐보며 그 자리에서 세트를 기록)을 구현하면서
세션의 기준을 **"루틴 1회 시작"** 에서 **"달력 날짜 하나"** 로 바꿨습니다. 하루 안에 서로 다른 루틴/개별 운동이
섞여 들어갈 수 있어야 목업처럼 "레그프레스 / 아웃싸이드 / 레그프레스" 같은 임의 조합이 가능하기 때문입니다.

### 하단 탭 4개
- **오늘**: 요청하신 목업 화면. 날짜 이동(◀/▶), 날짜 텍스트 클릭 시 달력 팝업, 운동별 카드(펼침/접힘, 계획·오늘·직전 총량, PR, 세트 입력, 메모), `+운동추가`.
- **달력**: 달력에서 날짜를 골라 바로 "오늘" 탭의 그 날짜 화면으로 이동 (오늘 화면 안에서 날짜를 눌러도 같은 달력이 뜨므로 사실상 진입점만 다름).
- **통계**: 자리만 만들어둔 placeholder. Phase 3(History/그래프)에서 채웁니다.
- **설정**: "루틴 관리"만 우선 제공. 루틴을 만들고 운동을 등록해두면, 오늘 화면의 `+운동추가 → 루틴 탭`에서 통째로 불러올 수 있습니다.

### 바뀐/새로 생긴 데이터 모델
- `WorkoutSession`: 이제 `routineId` 대신 `dateEpochDay`(날짜 1개, unique)를 갖습니다.
- `SessionExercise` (신규): 특정 날짜 세션에 "오늘 할 운동"으로 추가된 항목. 운동별 메모, 정렬 순서를 갖고,
  세트 기록은 여기 연결된 `exerciseId` 기준으로 저장됩니다.
- 이 변경 때문에 Room DB 버전을 2로 올렸고, 아직 마이그레이션을 작성하지 않아 `fallbackToDestructiveMigration()`으로 처리했습니다.
  (Phase 1 개발 단계라 기존 로컬 데이터가 날아가도 괜찮다고 판단했습니다 — 실사용자 데이터가 쌓이면 반드시 Migration을 작성해야 합니다.)

### "+운동추가" 팝업
- **운동 탭**: 지금까지 등록된 모든 운동(어느 루틴 소속이든 상관없이) 목록 + "+ 새 운동 만들기". 새로 만들면 `기본 운동`이라는 이름의 루틴에 자동으로 담깁니다(사용자가 직접 루틴을 고르지 않아도 되도록).
- **루틴 탭**: 루틴을 고르면 그 루틴에 속한 운동을 전부 오늘 세션에 한 번에 추가합니다(이미 추가된 운동은 중복 추가하지 않음).

### 카드 안 요약 라인 계산 방식 (임시)
- **오늘계획총량** = 목표중량 × ((최소반복+최대반복)/2) × 목표세트수
- **오늘수행총량** = 오늘 완료 체크한 세트들의 (중량×반복) 합
- **직전수행총량** = 이 운동이 포함됐던 가장 최근 이전 날짜의 (중량×반복) 합
- **PR** = 이 운동의 완료된 세트 중 역대 최고 중량

Phase 2에서 점진적 과부하 알고리즘을 붙이면 "계획"이 지난 수행 결과를 바탕으로 자동 추천되도록 바뀔 예정입니다.

# OFFICE version prompt & 구현결과


 1. 오늘 운동 입력 시 운동 처음 추가하면 세트 입력 칸 하나 기본 생성
2. 오늘 운동에 메모와 세트추가 버튼 순서 변경

. 운동/루틴추가 팝업 화면 수정
 - 부위별 색상지정
 - 운동목록 표시 형식 변경 
   이름(좌측정렬), 부위/입력방식(우측정렬)
 - 목록에 옅은 색으로 항목별 구분되는 가로 라인 표시
 - 루틴 목록 간격 적당히 띄우기
 - 각 항목 편집 시 이름 외에 세부 내용도 편집 할 수 있도록 변경
  

달력 화면 수정
 - 운동 입력 된 날짜에 부위별 색상 표시
 
   
오늘 화면 수정
 - 각 운동 롱프레스로 위치 변경 가능
 - 운동이름 옆에 부위(색상포함) 우측정렬로 표시 
 - 계획/직전/오늘/pr  접었을 때도 표시되도록
 - 숫자 입력칸 좌우에 버튼으로 수치 조절 가능하도록 
 - 숫자 입력칸이 포커싱 되면 삭제 없이 바로 수정 가능하도로고 text 선택 
 - 스와이프로 날짜 이동 가능
 
통계
 - 점진적 과부하를 잘 수행하고 있는지 분석이 가능한 통계를 시각화 해서 보여주도록

설정 
 - 데이터 export, import 
 - 
 
 
 통계
데이터: ExerciseSetDao.kt SetHistoryRow + observeCompletedSetHistory() — 완료(✓) 세트를 날짜/운동정보와 조인한 평면 목록 1개 쿼리. 집계는 StatsViewModel.kt에서.

핵심 지표는 Epley 추정 1RM (weight × (1 + reps/30)) — 무게와 횟수를 한 숫자로 합치므로 "무게 그대로, 횟수 증가"도 진전으로 잡힘.

판정: (경과일, e1RM) 최소제곱 회귀 → 주당 변화량. 임계값 max(0.4, 시작값×0.5%) 상대 기준 (절대값만 쓰면 스쿼트는 항상 진전, 레터럴레이즈는 항상 정체로 보임). 3회 미만은 NOT_ENOUGH_DATA.

StatsScreen.kt 3층 구성:

주간 총 볼륨 12주 막대 — 부위별 색 누적, 쉰 주는 얇은 선, 이번주 vs 지난주 %
운동 선택 칩 + 판정 카드 — 진전/정체/후퇴 배지 + 근거 문장 + PR / 기록횟수 / 최근4주 볼륨 변화%
추정 1RM 꺾은선 (Canvas 직접 구현, y축을 min~max로 늘려 작은 변화도 보임) + 세션별 볼륨 막대
시간 기반 운동은 e1RM 대신 초를 그대로 쓰고, 주간 볼륨 합산 시 초는 1/10로 눌러 kg×회와 섞음.

설정
기존 루틴관리 화면 제거 → 데이터 export/import.

BackupCodec.kt: 6개 테이블 전체를 id 보존해 JSON 직렬화. 의존성 안 늘리려고 Android 기본 org.json 사용
WorkoutRepository.kt: exportBackup() / importBackup() — 복원은 withTransaction으로 감싸고 외래키 방향대로 삭제(자식→부모)/삽입(부모→자식)
SettingsScreen.kt: SAF(CreateDocument/OpenDocument)로 파일 위치 선택 — 저장소 권한 불필요, 클라우드 드라이브도 가능. 기본 파일명 mytracker-backup-YYYYMMDD.json. 가져오기는 전체 덮어쓰기라 확인 팝업 필수
각 DAO에 getAll / deleteAll / restoreAll(REPLACE) 추가
삭제한 파일: ui/home/HomeScreen.kt, ui/routine/RoutineDetailScreen.kt — 옛 설정화면의 읽기 전용 루틴 목록/상세로, 유일한 참조처가 SettingsScreen이었고 기능은 오늘 화면 팝업의 루틴 편집으로 이미 대체됨.

1. 유산소 강도 표시 빠져있음
2. 운동추가시 부위 text box 여백 크기 줄이고 두 줄로 표시 하더라도 스크롤 하지 않도록 함
3. 초성검색 가능
4. 루틴 편집 시 기존에 선택 된 운동을 가장 먼저 보이도록함
5. 디버깅 - 새 루틴 만들기 완료 버튼이 안보임
6. 달력 날짜별 높이를 늘려서 날짜 밑에 부위 글씨까지 표시
7. 달력 월을 누르면 월간 이동 가능한 캘린더 팝업
8. 달력 아래 부위별 월간 간단한 정보 표시(이번달 시행횟수/총중량/마지막 수행 후 경과일)

8개 항목 반영 완료. 컴파일 검증 불가 — JAVA_HOME이 JRE 1.8을 가리켜 gradlew 실행 안 됨.

1. 유산소 강도 — ExerciseSet.weight 칸을 TIME 운동에서 "강도"(속도·레벨·경사)로 쓰도록 함. 스키마 변경 없음. TIME도 두 칸 모두 표시(강도 / 시간(초)), ± 스텝 INTENSITY_STEP = 0.5. TodayViewModel.kt에서 세트 추가 시 직전 강도를 이어받음. 통계·달력 총량 계산은 TIME의 weight를 무시하도록 이미 분리돼 있음.

2. 부위 칩 — BodyPartChipRow: FlowRow 자동 줄바꿈 + Box/Text 직접 구현 칩(패딩 8dp/3dp). M3 FilterChip은 최소높이 32dp 고정이라 못 줄임. 가로 스크롤 전부 제거.

3. 초성검색 — KoreanSearch.kt: 유니코드 (code-0xAC00)/588로 초성 추출. matchesSearch()를 운동/루틴/체크리스트 3곳에 적용. "ㅂㅊ" → 벤치프레스.

4. 루틴 편집 선택항목 우선 — initialCheckedIds를 remember로 스냅샷해 그것으로 정렬. 현재 checked로 정렬하면 체크 토글마다 항목이 튐.

5. 완료 버튼 안 보임 (버그) — 원인: Column 안에서 목록에 heightIn(max=220) 고정 높이를 줘서 내용 합이 팝업 최대높이(420)를 넘으면 버튼이 잘려나감. Column(fillMaxHeight) + 목록 weight(1f)로 바꿔 버튼을 항상 하단에 고정.

6. 달력 날짜 칸 — 52dp → 74dp, 색 점 대신 부위 이름을 9sp 색 글씨(옅은 배경)로 최대 2개, 초과분은 +N.

7. 월 선택 팝업 — 년월 텍스트 클릭 → MonthPickerDialog (년도 ◀▶ + 1~12월 3×4 격자, 현재월 테두리/선택월 채움).

8. 부위별 월간 요약 — SessionDao.kt에 쿼리 2개:

observeBodyPartMonthStats: session_exercise 기준 COUNT(DISTINCT dateEpochDay) (달력 색 표시와 개수 일치) + 완료 세트만 inputType으로 갈라 kg·회 / 초 각각 합산
observeBodyPartLastDays: 전체 기간 MAX(dateEpochDay) → 경과일
CalendarViewModel.kt에서 두 flow를 combine, 이번달에 안 한 부위도 "며칠 쉬었는지" 보이도록 합집합. 화면은 부위 · N회 · 총량 · N일 전 한 줄씩.
