package com.quanglewangle.peter.rocloc.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OperatorDao {

    @Query("SELECT * FROM operators WHERE callsign = :callsign")
    OperatorEntity getByCallsign(String callsign);

    @Query("SELECT * FROM operators ORDER BY lastSynced DESC LIMIT 100")
    List<OperatorEntity> getRecent();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OperatorEntity operator);

    @Query("DELETE FROM operators WHERE lastSynced < :before")
    void deleteOlderThan(long before);
}
