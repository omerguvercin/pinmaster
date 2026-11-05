package com.pinmaster.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KioskActivity extends Activity {

    private GridView gridView;
    private View exitTrigger;
    private TextView tvEmpty;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isExitUnlocked = false;
    private static final long HOLD_DURATION_MS = 1500; // 1.5 saniye
    private List<KioskItem> kioskItems = new ArrayList<>();

    private final Runnable exitRunnable = new Runnable() {
        @Override
        public void run() {
            isExitUnlocked = true;
            vibrate(120);
            exitKiosk();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kiosk);

        gridView    = findViewById(R.id.kiosk_grid);
        exitTrigger = findViewById(R.id.kiosk_exit_trigger);
        tvEmpty     = findViewById(R.id.tv_kiosk_empty);

        setupExitTrigger();
        loadKioskApps();

        // Vitrin modunu aktif yap
        SettingsManager.setKioskActive(this, true);

        // Mahremiyet Kalkanı
        if (SettingsManager.isPrivacyShieldEnabled(this)) {
            ShizukuHelper.setPrivacyShield(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SettingsManager.isKioskActive(this)) {
            if (SettingsManager.isPrivacyShieldEnabled(this)) {
                ShizukuHelper.setPrivacyShield(true);
            }
        }
    }

    private void setupExitTrigger() {
        if (exitTrigger == null) return;

        exitTrigger.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isExitUnlocked = false;
                    handler.postDelayed(exitRunnable, HOLD_DURATION_MS);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(exitRunnable);
                    if (!isExitUnlocked && event.getAction() == MotionEvent.ACTION_UP) {
                        Toast.makeText(this, "Çıkmak için logoya 1.5 saniye basılı tutun", Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });
    }

    private void loadKioskApps() {
        PackageManager pm = getPackageManager();
        Set<String> kioskPkgs = SettingsManager.getKioskPackages(this);
        kioskItems.clear();

        for (String pkg : kioskPkgs) {
            try {
                if (pkg.equals(getPackageName())) continue;
                Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                if (launchIntent == null) continue;

                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                String label = pm.getApplicationLabel(info).toString();
                Drawable icon = pm.getApplicationIcon(info);

                kioskItems.add(new KioskItem(label, pkg, icon, launchIntent));
            } catch (Exception ignored) {}
        }

        if (kioskItems.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            gridView.setVisibility(View.VISIBLE);
            KioskAdapter adapter = new KioskAdapter(this, kioskItems);
            gridView.setAdapter(adapter);

            gridView.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < kioskItems.size()) {
                    launchKioskApp(kioskItems.get(position));
                }
            });
        }
    }

    private void launchKioskApp(final KioskItem item) {
        if (item == null || item.launchIntent == null) return;
        try {
            item.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(item.launchIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void exitKiosk() {
        SettingsManager.setKioskActive(this, false);
        ShizukuHelper.setPrivacyShield(false);

        Toast.makeText(this, "Vitrin Modu Kapatıldı", Toast.LENGTH_SHORT).show();

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // Geri tuşuyla çıkışı engelle
    }

    // ─── Model & Adapter ──────────────────────────────────────────────────────

    private static class KioskItem {
        final String name;
        final String pkg;
        final Drawable icon;
        final Intent launchIntent;

        KioskItem(String name, String pkg, Drawable icon, Intent launchIntent) {
            this.name = name;
            this.pkg = pkg;
            this.icon = icon;
            this.launchIntent = launchIntent;
        }
    }

    private static class KioskAdapter extends BaseAdapter {
        private final Context context;
        private final List<KioskItem> list;

        KioskAdapter(Context context, List<KioskItem> list) {
            this.context = context;
            this.list = list;
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int position) { return list.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_kiosk_app, parent, false);
            }

            final KioskItem item = list.get(position);
            ImageView ivIcon = convertView.findViewById(R.id.kiosk_app_icon);
            TextView tvName  = convertView.findViewById(R.id.kiosk_app_name);

            ivIcon.setImageDrawable(item.icon);
            tvName.setText(item.name);

            return convertView;
        }
    }
}
