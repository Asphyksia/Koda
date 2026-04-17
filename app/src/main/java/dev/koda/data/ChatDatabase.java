package dev.koda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for conversations and messages.
 */
public class ChatDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "koda_chat.db";
    private static final int DB_VERSION = 1;

    private static ChatDatabase sInstance;

    public static synchronized ChatDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ChatDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    private ChatDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE conversations (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "title TEXT NOT NULL DEFAULT ''," +
            "session_id TEXT," +
            "model TEXT," +
            "created_at INTEGER NOT NULL," +
            "updated_at INTEGER NOT NULL" +
            ")");

        db.execSQL("CREATE TABLE messages (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "conversation_id INTEGER NOT NULL," +
            "role TEXT NOT NULL," +  // 'user', 'assistant', 'system', 'error'
            "content TEXT NOT NULL," +
            "created_at INTEGER NOT NULL," +
            "FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
            ")");

        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations go here
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // ========== Conversations ==========

    public long createConversation(String title, String model) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("model", model);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        return getWritableDatabase().insert("conversations", null, cv);
    }

    public void updateConversationTitle(long id, String title) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateConversationSessionId(long id, String sessionId) {
        ContentValues cv = new ContentValues();
        cv.put("session_id", sessionId);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void touchConversation(long id) {
        ContentValues cv = new ContentValues();
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("conversations", cv, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteConversation(long id) {
        getWritableDatabase().delete("conversations", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Get all conversations ordered by most recent.
     */
    public List<Conversation> getConversations() {
        List<Conversation> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT c.id, c.title, c.session_id, c.model, c.created_at, c.updated_at, " +
            "(SELECT content FROM messages WHERE conversation_id=c.id ORDER BY id DESC LIMIT 1) as last_message " +
            "FROM conversations c ORDER BY c.updated_at DESC", null);
        while (c.moveToNext()) {
            Conversation conv = new Conversation();
            conv.id = c.getLong(0);
            conv.title = c.getString(1);
            conv.sessionId = c.getString(2);
            conv.model = c.getString(3);
            conv.createdAt = c.getLong(4);
            conv.updatedAt = c.getLong(5);
            conv.lastMessage = c.getString(6);
            list.add(conv);
        }
        c.close();
        return list;
    }

    public Conversation getConversation(long id) {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT id, title, session_id, model, created_at, updated_at FROM conversations WHERE id=?",
            new String[]{String.valueOf(id)});
        Conversation conv = null;
        if (c.moveToFirst()) {
            conv = new Conversation();
            conv.id = c.getLong(0);
            conv.title = c.getString(1);
            conv.sessionId = c.getString(2);
            conv.model = c.getString(3);
            conv.createdAt = c.getLong(4);
            conv.updatedAt = c.getLong(5);
        }
        c.close();
        return conv;
    }

    // ========== Messages ==========

    public long addMessage(long conversationId, String role, String content) {
        ContentValues cv = new ContentValues();
        cv.put("conversation_id", conversationId);
        cv.put("role", role);
        cv.put("content", content);
        cv.put("created_at", System.currentTimeMillis());

        long id = getWritableDatabase().insert("messages", null, cv);
        touchConversation(conversationId);
        return id;
    }

    public void updateMessageContent(long messageId, String content) {
        ContentValues cv = new ContentValues();
        cv.put("content", content);
        getWritableDatabase().update("messages", cv, "id=?", new String[]{String.valueOf(messageId)});
    }

    public List<Message> getMessages(long conversationId) {
        List<Message> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT id, role, content, created_at FROM messages WHERE conversation_id=? ORDER BY id ASC",
            new String[]{String.valueOf(conversationId)});
        while (c.moveToNext()) {
            Message msg = new Message();
            msg.id = c.getLong(0);
            msg.role = c.getString(1);
            msg.content = c.getString(2);
            msg.createdAt = c.getLong(3);
            list.add(msg);
        }
        c.close();
        return list;
    }

    // ========== Data Classes ==========

    public static class Conversation {
        public long id;
        public String title;
        public String sessionId;
        public String model;
        public long createdAt;
        public long updatedAt;
        public String lastMessage;  // from query only
    }

    public static class Message {
        public long id;
        public String role;
        public String content;
        public long createdAt;
    }
}
