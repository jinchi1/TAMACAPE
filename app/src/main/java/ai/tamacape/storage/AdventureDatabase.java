package ai.tamacape.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import androidx.room.Upsert;
import java.util.List;

@Database(
    entities = {
        AdventureDatabase.Profile.class, AdventureDatabase.Checkpoint.class,
        AdventureDatabase.OwnedItem.class, AdventureDatabase.Reward.class
    },
    version = 3,
    exportSchema = true
)
public abstract class AdventureDatabase extends RoomDatabase {
    public abstract Saves saves();

    @Entity
    public static class Profile {
        @PrimaryKey public int id;
        public int revision;
        public int cleared;
        public int xp;
        public int coins;
        @Nullable public Integer stage;
        @Nullable public String costume;
        @Nullable public String furniture;
        @NonNull @ColumnInfo(defaultValue = "'mobi'") public String companion = "mobi";
    }

    @Entity
    public static class Checkpoint {
        @PrimaryKey public int stage;
        public int puzzle;
        public boolean explained;
        @NonNull public String selection = "";
        @ColumnInfo(defaultValue = "0") public boolean usedHint;
        @ColumnInfo(defaultValue = "0") public boolean madeMistake;
        @ColumnInfo(defaultValue = "0") public int stars;
    }

    @Entity
    public static class OwnedItem {
        @PrimaryKey @NonNull public String name = "";
    }

    @Entity
    public static class Reward {
        @PrimaryKey @NonNull public String eventKey = "";
    }

    @Dao
    public interface Saves {
        @Query("SELECT * FROM Profile WHERE id = 0") Profile profile();
        @Query("SELECT * FROM Checkpoint") List<Checkpoint> checkpoints();
        @Query("SELECT * FROM OwnedItem") List<OwnedItem> inventory();
        @Upsert void putProfile(Profile profile);
        @Upsert void putCheckpoints(List<Checkpoint> checkpoints);
        @Insert(onConflict = OnConflictStrategy.IGNORE) void putItems(List<OwnedItem> items);
        @Insert void insertReward(Reward reward);
        @Query("DELETE FROM Checkpoint") void clearCheckpoints();
        @Query("DELETE FROM OwnedItem") void clearItems();
        @Query("DELETE FROM Reward") void clearRewards();
    }
}
