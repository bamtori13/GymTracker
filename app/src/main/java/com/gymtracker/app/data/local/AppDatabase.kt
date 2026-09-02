package com.gymtracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gymtracker.app.data.local.dao.ExerciseDao
import com.gymtracker.app.data.local.dao.ExerciseSetDao
import com.gymtracker.app.data.local.dao.PeriodDayDao
import com.gymtracker.app.data.local.dao.RoutineDao
import com.gymtracker.app.data.local.dao.RoutineExerciseDao
import com.gymtracker.app.data.local.dao.SessionDao
import com.gymtracker.app.data.local.dao.SessionExerciseDao
import com.gymtracker.app.data.local.entity.Exercise
import com.gymtracker.app.data.local.entity.ExerciseSet
import com.gymtracker.app.data.local.entity.PeriodDay
import com.gymtracker.app.data.local.entity.PersonalRecord
import com.gymtracker.app.data.local.entity.RoutineExercise
import com.gymtracker.app.data.local.entity.SessionExercise
import com.gymtracker.app.data.local.entity.WorkoutRoutine
import com.gymtracker.app.data.local.entity.WorkoutSession

@Database(
    entities = [
        WorkoutRoutine::class,
        Exercise::class,
        WorkoutSession::class,
        SessionExercise::class,
        ExerciseSet::class,
        PersonalRecord::class,
        RoutineExercise::class,
        PeriodDay::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routineDao(): RoutineDao
    abstract fun routineExerciseDao(): RoutineExerciseDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionExerciseDao(): SessionExerciseDao
    abstract fun exerciseSetDao(): ExerciseSetDao
    abstract fun periodDayDao(): PeriodDayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_tracker.db"
                )
                    // 세션 스키마를 "루틴 기준"에서 "날짜 기준"으로 바꾸면서 마이그레이션을 아직 작성하지 않았다.
                    // 정식 배포 전(Phase 1 개발 단계)이므로 파괴적 마이그레이션으로 처리한다.
                    // 실제 사용자 데이터가 쌓인 뒤에는 반드시 Migration을 작성해야 한다.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
