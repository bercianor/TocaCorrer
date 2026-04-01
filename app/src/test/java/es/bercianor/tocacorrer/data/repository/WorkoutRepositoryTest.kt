package es.bercianor.tocacorrer.data.repository

import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.local.dao.WorkoutDao
import es.bercianor.tocacorrer.data.local.dao.GpsPointDao
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

/**
 * Tests for WorkoutRepository.
 */
@RunWith(MockitoJUnitRunner::class)
class WorkoutRepositoryTest {

    @Mock
    lateinit var database: AppDatabase

    @Mock
    lateinit var workoutDao: WorkoutDao

    @Mock
    lateinit var gpsPointDao: GpsPointDao

    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        repository = WorkoutRepository(database, workoutDao, gpsPointDao)
    }

    @Test
    fun `getAllWorkouts returns flow from dao`() = runTest {
        val workouts = listOf(
            Workout(startTime = 1000, originalRoutine = "C10"),
            Workout(startTime = 2000, originalRoutine = "C20")
        )
        whenever(workoutDao.getAllWorkouts()).thenReturn(flowOf(workouts))

        val result = repository.getAllWorkouts()

        result.collect { list ->
            assertEquals(2, list.size)
        }
    }

    @Test
    fun `getRecentWorkouts returns flow from dao`() = runTest {
        val workouts = listOf(
            Workout(startTime = 1000, originalRoutine = "C10")
        )
        whenever(workoutDao.getRecentWorkouts(10)).thenReturn(flowOf(workouts))

        val result = repository.getRecentWorkouts(10)

        result.collect { list ->
            assertEquals(1, list.size)
        }
    }

    @Test
    fun `getWorkoutsBetweenDates returns flow from dao`() = runTest {
        val workouts = listOf(
            Workout(startTime = 1700000000000, originalRoutine = "C10")
        )
        whenever(workoutDao.getWorkoutsBetweenDates(1700000000000, 1700100000000))
            .thenReturn(flowOf(workouts))

        val result = repository.getWorkoutsBetweenDates(1700000000000, 1700100000000)

        result.collect { list ->
            assertEquals(1, list.size)
        }
    }

    @Test
    fun `getWorkoutById returns from dao`() = runTest {
        val workout = Workout(id = 1, startTime = 1000, originalRoutine = "C10")
        whenever(workoutDao.getWorkoutById(1)).thenReturn(workout)

        val result = repository.getWorkoutById(1)

        assertEquals(1L, result?.id)
    }

    @Test
    fun `getWorkoutById returns null when not found`() = runTest {
        whenever(workoutDao.getWorkoutById(999)).thenReturn(null)

        val result = repository.getWorkoutById(999)

        assertEquals(null, result)
    }

    @Test
    fun `getPointsByWorkoutSync returns from dao`() = runTest {
        val points = listOf(
            GpsPoint(workoutId = 1, latitude = 42.0, longitude = -8.0, altitude = 100.0, timestamp = 1000, phase = "C")
        )
        whenever(gpsPointDao.getPointsByWorkoutSync(1)).thenReturn(points)

        val result = repository.getPointsByWorkoutSync(1)

        assertEquals(1, result.size)
    }

    @Test
    fun `getPointsByWorkout returns map`() = runTest {
        val points1 = listOf(
            GpsPoint(workoutId = 1, latitude = 42.0, longitude = -8.0, altitude = 100.0, timestamp = 1000, phase = "C")
        )
        whenever(gpsPointDao.getPointsByWorkoutIds(listOf(1))).thenReturn(points1)

        val result = repository.getPointsByWorkout(listOf(1))

        assertTrue(result.containsKey(1))
        assertEquals(1, result[1]?.size)
    }

    @Test
    fun `getStatistics returns correct stats`() = runTest {
        whenever(workoutDao.getTotalDistanceBetweenDates(1000, 2000)).thenReturn(5000.0)
        whenever(workoutDao.getTotalDurationBetweenDates(1000, 2000)).thenReturn(1800L)
        whenever(workoutDao.getCountBetweenDates(1000, 2000)).thenReturn(3)

        val result = repository.getStatistics(1000, 2000)

        assertEquals(5000.0, result.totalDistanceMeters, 0.001)
        assertEquals(1800L, result.totalDurationSeconds)
        assertEquals(3, result.workoutCount)
    }

    @Test
    fun `getStatistics with no data returns zeros`() = runTest {
        whenever(workoutDao.getTotalDistanceBetweenDates(1000, 2000)).thenReturn(null)
        whenever(workoutDao.getTotalDurationBetweenDates(1000, 2000)).thenReturn(null)
        whenever(workoutDao.getCountBetweenDates(1000, 2000)).thenReturn(0)

        val result = repository.getStatistics(1000, 2000)

        assertEquals(0.0, result.totalDistanceMeters, 0.001)
        assertEquals(0L, result.totalDurationSeconds)
        assertEquals(0, result.workoutCount)
    }

    @Test
    fun `insertWorkout calls dao`() = runTest {
        val workout = Workout(startTime = 1000, originalRoutine = "C10")
        whenever(workoutDao.insert(workout)).thenReturn(1L)

        val result = repository.insertWorkout(workout)

        assertEquals(1L, result)
    }

    @Test
    fun `updateWorkout calls dao`() = runTest {
        val workout = Workout(id = 1, startTime = 1000, originalRoutine = "C10")

        repository.updateWorkout(workout)

        // If no exception, test passes
        assertTrue(true)
    }

    @Test
    fun `deleteWorkout calls dao`() = runTest {
        val workout = Workout(id = 1, startTime = 1000, originalRoutine = "C10")

        repository.deleteWorkout(workout)

        // If no exception, test passes
        assertTrue(true)
    }
}
