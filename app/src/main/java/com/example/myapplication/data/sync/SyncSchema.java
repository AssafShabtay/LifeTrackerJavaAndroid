package com.example.myapplication.data.sync;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Auxiliary tables do not change Room's entity schema. Triggers journal every DAO write atomically. */
public final class SyncSchema extends RoomDatabase.Callback {
    public static final String[] TABLES = {"places", "movement_activities", "still_locations", "route_points"};

    @Override public void onOpen(@NonNull SupportSQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_state (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
            db.execSQL("INSERT OR IGNORE INTO cloud_state VALUES ('applying', '0')");
            db.execSQL("CREATE TABLE IF NOT EXISTS cloud_records (sync_id TEXT PRIMARY KEY NOT NULL, " +
                    "table_name TEXT NOT NULL, local_id INTEGER, version INTEGER NOT NULL DEFAULT 1, " +
                    "dirty INTEGER NOT NULL DEFAULT 1, deleted INTEGER NOT NULL DEFAULT 0, mutation TEXT NOT NULL DEFAULT '', " +
                    "UNIQUE(table_name, local_id))");
            db.execSQL("CREATE INDEX IF NOT EXISTS cloud_pending ON cloud_records(table_name, dirty)");
            for (String table : TABLES) {
                db.execSQL("INSERT OR IGNORE INTO cloud_records(sync_id, table_name, local_id) " +
                        "SELECT lower(hex(randomblob(16))), '" + table + "', id FROM " + table);
                String enabled = " WHEN (SELECT value FROM cloud_state WHERE key='applying')='0' ";
                // INSERT OR REPLACE in an existing DAO must retain the record's cloud identity.
                db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_" + table + "_insert AFTER INSERT ON " + table + enabled +
                        "BEGIN INSERT OR IGNORE INTO cloud_records(sync_id,table_name,local_id) VALUES " +
                        "(lower(hex(randomblob(16))), '" + table + "', NEW.id); " +
                        "UPDATE cloud_records SET dirty=1, deleted=0, version=version+1, mutation=lower(hex(randomblob(16))) WHERE table_name='" +
                        table + "' AND local_id=NEW.id; END");
                db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_" + table + "_update AFTER UPDATE ON " + table + enabled +
                        "BEGIN UPDATE cloud_records SET dirty=1, version=version+1, mutation=lower(hex(randomblob(16))) WHERE table_name='" + table +
                        "' AND local_id=NEW.id; END");
                db.execSQL("CREATE TRIGGER IF NOT EXISTS cloud_" + table + "_delete AFTER DELETE ON " + table + enabled +
                        "BEGIN UPDATE cloud_records SET dirty=1, deleted=1, version=version+1, mutation=lower(hex(randomblob(16))) WHERE table_name='" + table +
                        "' AND local_id=OLD.id; END");
            }
            db.execSQL("UPDATE cloud_records SET mutation=lower(hex(randomblob(16))) WHERE mutation=''");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
