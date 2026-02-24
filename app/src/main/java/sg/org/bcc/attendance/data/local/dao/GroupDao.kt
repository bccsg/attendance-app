package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sg.org.bcc.attendance.data.local.entities.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<Group>)

    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT groupId FROM groups")
    suspend fun getAllGroupIds(): List<String>

    @Query("UPDATE groups SET notExistOnCloud = 1 WHERE groupId IN (:ids)")
    suspend fun markAsMissingOnCloud(ids: List<String>)

    @Query("SELECT * FROM groups WHERE notExistOnCloud = 1")
    fun getMissingOnCloudGroups(): Flow<List<Group>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: Group)

    @Query("DELETE FROM groups WHERE groupId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM groups")
    suspend fun clearAll()

    @Query("DELETE FROM groups WHERE notExistOnCloud = 1")
    suspend fun purgeAllMissingOnCloud()
}
