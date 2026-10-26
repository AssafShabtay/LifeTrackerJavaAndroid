package com.example.myapplication.data.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local side of the protocol. All snapshots and acknowledgements are transaction-safe. */
public final class SyncStore {
    private final RoomDatabase room;
    private final SupportSQLiteDatabase db;
    public SyncStore(RoomDatabase room) {
        this.room = room;
        db = room.getOpenHelper().getWritableDatabase();
    }

    public String state(String key) {
        try (Cursor c = db.query("SELECT value FROM cloud_state WHERE key=?", new Object[]{key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }
    public void state(String key, String value) {
        db.execSQL("INSERT OR REPLACE INTO cloud_state(key,value) VALUES (?,?)", new Object[]{key, value});
    }
    public boolean bindOwner(String uid) {
        return room.runInTransaction(() -> {
            String owner = state("owner");
            if (owner != null && !owner.equals(uid)) return false;
            state("owner", uid);
            return true;
        });
    }

    public static String parentTable(String table) {
        if (table.equals("still_locations")) return "places";
        if (table.equals("route_points")) return "movement_activities";
        return null;
    }
    public static String parentColumn(String table) {
        return table.equals("still_locations") ? "placeId" : "movementActivityId";
    }
    public Long localId(String table, String syncId) {
        try (Cursor c = db.query("SELECT local_id FROM cloud_records WHERE table_name=? AND sync_id=? AND deleted=0",
                new Object[]{table, syncId})) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : null;
        }
    }
    private String syncId(String table, long localId) {
        try (Cursor c = db.query("SELECT sync_id FROM cloud_records WHERE table_name=? AND local_id=?",
                new Object[]{table, localId})) {
            if (!c.moveToFirst()) throw new IllegalStateException("Missing relationship identity");
            return c.getString(0);
        }
    }

    public static final class Pending {
        public final String id;
        public final long version;
        public final Map<String, Object> document;
        Pending(String id, long version, Map<String, Object> document) {
            this.id = id; this.version = version; this.document = document;
        }
    }
    // Keyset pagination also advances past ongoing activities, which stay queued until completed.
    public List<Pending> pending(String table, String after, int limit) {
        return room.runInTransaction(() -> {
            List<Pending> result = new ArrayList<>();
            try (Cursor c = db.query("SELECT sync_id,local_id,version,deleted,mutation FROM cloud_records " +
                    "WHERE table_name=? AND dirty=1 AND sync_id>? ORDER BY sync_id LIMIT ?",
                    new Object[]{table, after, limit})) {
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    boolean deleted = c.getInt(3) == 1;
                    Map<String, Object> row = deleted ? new HashMap<>() : row(table, c.getLong(1));
                    Map<String, Object> doc = null;
                    if (row != null) {
                        doc = new HashMap<>();
                        doc.put("schema", 1L);
                        doc.put("deleted", deleted);
                        doc.put("fields", row);
                        doc.put("mutation", c.getString(4));
                    }
                    result.add(new Pending(id, c.getLong(2), doc));
                }
            }
            return result;
        });
    }
    private Map<String, Object> row(String table, long id) {
        try (Cursor c = db.query("SELECT * FROM " + table + " WHERE id=?", new Object[]{id})) {
            if (!c.moveToFirst()) throw new IllegalStateException("Missing queued row");
            int end = c.getColumnIndex("endTimeDate");
            if (end >= 0 && c.isNull(end)) return null;
            if (table.equals("route_points")) {
                try (Cursor parent = db.query("SELECT endTimeDate FROM movement_activities WHERE id=?",
                        new Object[]{c.getLong(c.getColumnIndexOrThrow("movementActivityId"))})) {
                    if (!parent.moveToFirst() || parent.isNull(0)) return null;
                }
            }
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < c.getColumnCount(); i++) {
                String name = c.getColumnName(i);
                if (name.equals("id")) continue;
                Object value;
                switch (c.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL: value = null; break;
                    case Cursor.FIELD_TYPE_INTEGER: value = c.getLong(i); break;
                    case Cursor.FIELD_TYPE_FLOAT: value = c.getDouble(i); break;
                    case Cursor.FIELD_TYPE_STRING: value = c.getString(i); break;
                    default: throw new IllegalStateException("Unsupported sync field");
                }
                if (parentTable(table) != null && name.equals(parentColumn(table)) && value != null) {
                    value = syncId(parentTable(table), ((Number) value).longValue());
                }
                values.put(name, value);
            }
            return values;
        }
    }
    public void acknowledge(Pending pending) {
        db.execSQL("UPDATE cloud_records SET dirty=0 WHERE sync_id=? AND version=?",
                new Object[]{pending.id, pending.version});
    }

    /** Returns false if a local write is pending; callers must retry without advancing the cursor. */
    public boolean apply(String table, String id, Map<String, Object> document) {
        if (!Long.valueOf(1).equals(document.get("schema"))) throw new IllegalStateException("Update the app to restore this data");
        return room.runInTransaction(() -> {
            boolean deleted = Boolean.TRUE.equals(document.get("deleted"));
            Long local = null;
            boolean known = false;
            try (Cursor c = db.query("SELECT local_id,dirty,deleted FROM cloud_records WHERE sync_id=? AND table_name=?",
                    new Object[]{id, table})) {
                if (c.moveToFirst()) {
                    known = true;
                    if (!c.isNull(0)) local = c.getLong(0);
                    if (!deleted && c.getInt(1) == 1) return false;
                    // A tombstone is permanent, including when an older download arrives late.
                    if (!deleted && c.getInt(2) == 1) return true;
                }
            }
            state("applying", "1");
            try {
                if (deleted) {
                    if (local != null) {
                        if (table.equals("movement_activities")) {
                            db.execSQL("UPDATE cloud_records SET deleted=1,dirty=1,version=version+1,mutation=lower(hex(randomblob(16))) WHERE table_name='route_points' AND deleted=0 " +
                                    "AND local_id IN (SELECT id FROM route_points WHERE movementActivityId=?)", new Object[]{local});
                        }
                        db.delete(table, "id=?", new Object[]{local});
                    }
                } else {
                    @SuppressWarnings("unchecked") Map<String, Object> fields = (Map<String, Object>) document.get("fields");
                    ContentValues values = new ContentValues();
                    // Read known columns instead of treating remote keys as SQL identifiers.
                    try (Cursor columns = db.query("PRAGMA table_info(" + table + ")")) {
                        while (columns.moveToNext()) {
                            String name = columns.getString(columns.getColumnIndexOrThrow("name"));
                            if (name.equals("id")) continue;
                            if (!fields.containsKey(name)) throw new IllegalStateException("Incomplete cloud record");
                            Object value = fields.get(name);
                            if (parentTable(table) != null && name.equals(parentColumn(table)) && value != null) {
                                value = localId(parentTable(table), (String) value);
                                if (value == null && table.equals("route_points")) {
                                    throw new IllegalStateException("Route parent is missing; retry restore");
                                }
                            }
                            if (value == null) values.putNull(name);
                            else if (value instanceof Double || value instanceof Float) values.put(name, ((Number) value).doubleValue());
                            else if (value instanceof Number) values.put(name, ((Number) value).longValue());
                            else if (value instanceof String) values.put(name, (String) value);
                            else throw new IllegalStateException("Invalid cloud field");
                        }
                    }
                    if (local == null) local = db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
                    else db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id=?", new Object[]{local});
                }
                if (!known) {
                    db.execSQL("INSERT INTO cloud_records(sync_id,table_name,local_id,dirty,deleted) VALUES (?,?,?,0,?)",
                            new Object[]{id, table, local, deleted ? 1 : 0});
                } else {
                    db.execSQL("UPDATE cloud_records SET dirty=0,deleted=? WHERE sync_id=?", new Object[]{deleted ? 1 : 0, id});
                }
            } finally { state("applying", "0"); }
            return true;
        });
    }
}
