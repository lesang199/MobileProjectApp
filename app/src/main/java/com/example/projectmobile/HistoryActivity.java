package com.example.projectmobile;

import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.projectmobile.model.Post;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    private RecyclerView recyclerHistory;
    private HistoryAdapter historyAdapter;
    private List<Post> postList;
    private FloatingActionButton fabDeleteHistory;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private DatabaseHelper dbHelper; // 1. Khai báo DatabaseHelper

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Khởi tạo Firebase & SQLite
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        dbHelper = new DatabaseHelper(this); // 2. Khởi tạo DatabaseHelper

        // Ánh xạ Views
        recyclerHistory = findViewById(R.id.recyclerHistory);
        fabDeleteHistory = findViewById(R.id.fabDeleteHistory);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));

        // Khởi tạo danh sách và adapter
        postList = new ArrayList<>();
        historyAdapter = new HistoryAdapter(postList, this);
        recyclerHistory.setAdapter(historyAdapter);

        // Gắn sự kiện cho nút xóa
        fabDeleteHistory.setOnClickListener(v -> showDeleteConfirmationDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    // 3. Hàm kiểm tra kết nối mạng (Mới thêm)
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void loadHistory() {
        // --- TRƯỜNG HỢP 1: KHÔNG CÓ MẠNG (OFFLINE) ---
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Mất mạng: Tải lịch sử từ SQLite");
            Toast.makeText(this, "Bạn đang xem lịch sử Offline", Toast.LENGTH_SHORT).show();

            // Lấy dữ liệu từ SQLite (Yêu cầu DatabaseHelper phải có hàm getAllHistoryOffline)
            List<Post> offlineList = dbHelper.getAllHistoryOffline();

            postList.clear();
            postList.addAll(offlineList);
            historyAdapter.notifyDataSetChanged();

            // Ẩn nút xóa khi offline để tránh lỗi đồng bộ
            fabDeleteHistory.setVisibility(View.GONE);

            if (postList.isEmpty()) {
                Toast.makeText(this, "Chưa có bài viết lưu trong máy.", Toast.LENGTH_SHORT).show();
            }
            return; // Kết thúc hàm, không chạy code Firebase bên dưới
        }

        // --- TRƯỜNG HỢP 2: CÓ MẠNG (ONLINE) - Code cũ của bạn ---
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem lịch sử", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String userId = currentUser.getUid();

        db.collection("users").document(userId).collection("history")
                .orderBy("readAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(historySnapshots -> {
                    if (historySnapshots.isEmpty()) {
                        postList.clear();
                        historyAdapter.notifyDataSetChanged();
                        fabDeleteHistory.setVisibility(View.GONE);
                        Toast.makeText(this, "Lịch sử đọc Online trống.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    fabDeleteHistory.setVisibility(View.VISIBLE);

                    List<String> postIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : historySnapshots) {
                        String postId = doc.getString("postId");
                        if (postId != null && !postId.isEmpty()) {
                            postIds.add(postId);
                        }
                    }

                    if (!postIds.isEmpty()) {
                        fetchPostsDetails(postIds);
                    } else {
                        postList.clear();
                        historyAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi Firebase, chuyển sang Offline", e);
                    // 4. Fallback: Nếu có mạng nhưng lỗi Firebase -> Vẫn lấy từ SQLite
                    List<Post> offlineList = dbHelper.getAllHistoryOffline();
                    postList.clear();
                    postList.addAll(offlineList);
                    historyAdapter.notifyDataSetChanged();
                });
    }

    private void fetchPostsDetails(final List<String> postIds) {
        // Lưu ý: Firebase giới hạn whereIn tối đa 10 phần tử.
        // Nếu lịch sử > 10 bài có thể lỗi, nhưng tạm thời giữ nguyên logic của bạn.
        db.collection("posts").whereIn(FieldPath.documentId(), postIds)
                .get()
                .addOnSuccessListener(postsSnapshots -> {
                    postList.clear();
                    if (!postsSnapshots.isEmpty()) {
                        for (DocumentSnapshot postDoc : postsSnapshots) {
                            Post post = postDoc.toObject(Post.class);
                            if (post != null) {
                                post.setPostId(postDoc.getId());
                                postList.add(post);
                            }
                        }
                        // Sắp xếp lại theo thứ tự xem gần nhất (dựa vào list postIds ban đầu)
                        // Cần xử lý cẩn thận nếu postId không tồn tại trong list
                        Collections.sort(postList, (p1, p2) -> {
                            int index1 = postIds.indexOf(p1.getPostId());
                            int index2 = postIds.indexOf(p2.getPostId());
                            return Integer.compare(index1, index2);
                        });

                        historyAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi khi tải chi tiết bài viết", e);
                });
    }

    private void showDeleteConfirmationDialog() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Vui lòng kết nối mạng để xóa lịch sử.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xóa Lịch Sử")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ lịch sử đọc báo không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    deleteHistory();
                })
                .setNegativeButton("Hủy", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteHistory() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        CollectionReference historyRef = db.collection("users").document(userId).collection("history");

        historyRef.get().addOnSuccessListener(queryDocumentSnapshots -> {
            WriteBatch batch = db.batch();
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                batch.delete(doc.getReference());
            }
            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(HistoryActivity.this, "Đã xóa toàn bộ lịch sử Online.", Toast.LENGTH_SHORT).show();

                // 5. Xóa cả lịch sử Offline cho đồng bộ (Tùy chọn)
                dbHelper.clearOfflineHistory();

                postList.clear();
                historyAdapter.notifyDataSetChanged();
                fabDeleteHistory.setVisibility(View.GONE);
            }).addOnFailureListener(e -> {
                Toast.makeText(HistoryActivity.this, "Xóa lịch sử thất bại.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Lỗi khi xóa lịch sử", e);
            });
        });
    }
}