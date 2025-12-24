package com.example.projectmobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;
import com.example.projectmobile.model.Post; // Import model Post
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "Sportisa.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_POSTS = "posts";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_IMAGE_URL = "image_url";

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_POSTS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_TITLE + " TEXT, " +
                COLUMN_CONTENT + " TEXT, " +
                COLUMN_CATEGORY + " TEXT, " +
                COLUMN_IMAGE_URL + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        onCreate(db);
    }

    // --- 1. Hàm thêm bài viết (Có kiểm tra trùng lặp) ---
    public boolean addPost(Post post) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Kiểm tra xem bài này đã tồn tại chưa (Dựa vào Title hoặc ID nếu có)
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_POSTS + " WHERE " + COLUMN_TITLE + "=?", new String[]{post.getTitle()});
        if (cursor.getCount() > 0) {
            cursor.close();
            return false; // Đã có rồi thì không thêm nữa
        }
        cursor.close();

        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, post.getTitle());
        values.put(COLUMN_CONTENT, post.getContent()); // Lưu nội dung để đọc offline
        values.put(COLUMN_CATEGORY, post.getCategory()); // Lưu danh mục nếu cần
        values.put(COLUMN_IMAGE_URL, post.getImageUrl());

        long result = db.insert(TABLE_POSTS, null, values);
        return result != -1;
    }

    // --- 2. HÀM MỚI: Lấy danh sách bài viết (Để xem Offline) ---
    public List<Post> getAllHistoryOffline() {
        List<Post> postList = new ArrayList<>();
        // Lấy bài mới xem nhất lên đầu (DESC)
        String selectQuery = "SELECT * FROM " + TABLE_POSTS + " ORDER BY " + COLUMN_ID + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Post post = new Post();
                // Ánh xạ từ SQLite sang Object Post
                // Lưu ý: Class Post của bạn cần có các hàm set tương ứng

                // Lấy cột Title
                int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
                if (titleIndex != -1) post.setTitle(cursor.getString(titleIndex));

                // Lấy cột Content
                int contentIndex = cursor.getColumnIndex(COLUMN_CONTENT);
                if (contentIndex != -1) post.setContent(cursor.getString(contentIndex));

                // Lấy cột Image
                int imgIndex = cursor.getColumnIndex(COLUMN_IMAGE_URL);
                if (imgIndex != -1) post.setImageUrl(cursor.getString(imgIndex));

                // Lấy cột Category (nếu class Post có setCategory)
                int catIndex = cursor.getColumnIndex(COLUMN_CATEGORY);
                if (catIndex != -1) post.setCategory(cursor.getString(catIndex));

                postList.add(post);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return postList;
    }

    // Hàm xóa lịch sử offline
    public void clearOfflineHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_POSTS);
        db.close();
    }
}