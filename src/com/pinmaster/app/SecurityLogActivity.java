package com.pinmaster.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SecurityLogActivity extends Activity {

    private ListView listView;
    private LinearLayout layoutEmpty;
    private Button btnClearAll;
    private Button btnBack;
    private List<IntruderLog> logList = new ArrayList<>();
    private LogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_log);

        btnBack = findViewById(R.id.btn_back_log);
        btnClearAll = findViewById(R.id.btn_clear_all_logs);
        listView = findViewById(R.id.list_intruder_logs);
        layoutEmpty = findViewById(R.id.layout_empty_logs);

        btnBack.setOnClickListener(v -> finish());

        btnClearAll.setOnClickListener(v -> {
            if (logList.isEmpty()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Günlüğü Temizle")
                    .setMessage("Tüm casus fotoğrafları ve güvenlik kayıtları silinsin mi?")
                    .setPositiveButton("Temizle", (d, w) -> {
                        IntruderManager.clearAllLogs(this);
                        loadLogs();
                    })
                    .setNegativeButton("İptal", null)
                    .show();
        });

        adapter = new LogAdapter();
        listView.setAdapter(adapter);

        loadLogs();
    }

    private void loadLogs() {
        logList = IntruderManager.getLogs(this);
        adapter.notifyDataSetChanged();
        boolean hasItems = !logList.isEmpty();
        layoutEmpty.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        listView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        btnClearAll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private void showPhotoDialog(IntruderLog log) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_photo_view, null);
        ImageView ivFull = dialogView.findViewById(R.id.iv_dialog_full_photo);
        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_log_title);
        TextView tvSub = dialogView.findViewById(R.id.tv_dialog_log_subtitle);
        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_log_date);
        Button btnClose = dialogView.findViewById(R.id.btn_dialog_close_photo);

        tvTitle.setText(log.getAppName());
        tvSub.setText(log.getReason());
        tvDate.setText(log.getFormattedDate());

        try {
            File imgFile = new File(log.getPhotoPath());
            if (imgFile.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                if (bmp != null) {
                    ivFull.setImageBitmap(bmp);
                }
            }
        } catch (Exception ignored) {}

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private class LogAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return logList.size();
        }

        @Override
        public IntruderLog getItem(int position) {
            return logList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SecurityLogActivity.this).inflate(R.layout.item_intruder_log, parent, false);
            }

            final IntruderLog log = getItem(position);

            ImageView ivThumb = convertView.findViewById(R.id.iv_intruder_thumb);
            TextView tvApp = convertView.findViewById(R.id.tv_intruder_app);
            TextView tvReason = convertView.findViewById(R.id.tv_intruder_reason);
            TextView tvTime = convertView.findViewById(R.id.tv_intruder_time);
            Button btnDelete = convertView.findViewById(R.id.btn_delete_log);

            tvApp.setText(log.getAppName());
            tvReason.setText(log.getReason());
            tvTime.setText(log.getFormattedDate());

            // Küçük önizleme yükleme
            try {
                File imgFile = new File(log.getPhotoPath());
                if (imgFile.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4; // Hafızayı korumak için 1/4 ölçek
                    Bitmap thumb = BitmapFactory.decodeFile(imgFile.getAbsolutePath(), opts);
                    if (thumb != null) {
                        ivThumb.setImageBitmap(thumb);
                    }
                }
            } catch (Exception ignored) {}

            convertView.setOnClickListener(v -> showPhotoDialog(log));

            btnDelete.setOnClickListener(v -> {
                IntruderManager.deleteLog(SecurityLogActivity.this, log.getId());
                loadLogs();
            });

            return convertView;
        }
    }
}
