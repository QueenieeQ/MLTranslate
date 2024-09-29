/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.MLTranslator.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import nie.translator.MLTranslator.database.entities.RecentPeerEntity;

@Dao
public interface MyDao{
    //inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentPeers(RecentPeerEntity... recentPeerEntities);

    //updates
    @Update
    void updateRecentPeers(RecentPeerEntity... recentPeerEntities);

    //deletes
    @Delete
    void deleteRecentPeers(RecentPeerEntity... recentPeerEntities);

    //select
    @Query("SELECT * FROM RecentPeerEntity")
    RecentPeerEntity[] loadRecentPeers();

    @Query("SELECT * FROM RecentPeerEntity where deviceId=:deviceId")
    RecentPeerEntity loadRecentPeer(String deviceId);

    @Query("SELECT * FROM RecentPeerEntity where uniqueName=:uniqueName")
    RecentPeerEntity loadRecentPeerByName(String uniqueName);

}
