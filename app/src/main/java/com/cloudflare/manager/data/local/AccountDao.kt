package com.cloudflare.manager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE accounts SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query("UPDATE accounts SET isCurrent = 1 WHERE id = :id")
    suspend fun setCurrent(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
