package com.pinmaster.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class IntruderManager {

    private static final String TAG = "IntruderManager";
    private static final String LOG_FILE_NAME = "intruders.json";
    private static volatile long lastCaptureTime = 0;

    /**
     * Ön kamera ile arka planda sessizce 1 kare fotoğraf çeker ve günlüğe kaydeder.
     */
    public static void captureSilentPhoto(final Context context, final String targetPkg, final String reason) {
        if (context == null) return;

        // İzin kontrolü
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Kamera izni bulunmadığından casus fotoğraf çekilemedi.");
            return;
        }

        // Arka arkaya tetiklemeleri önle (En az 4 saniye aralık)
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < 4000) {
            Log.i(TAG, "Casus fotoğrafı henüz yeni çekildi, atlanıyor.");
            return;
        }
        lastCaptureTime = now;

        new Thread(() -> {
            HandlerThread backgroundThread = null;
            try {
                final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (manager == null) return;

                // Ön kamerayı bul
                String frontCameraId = null;
                CameraCharacteristics frontChars = null;
                for (String camId : manager.getCameraIdList()) {
                    CameraCharacteristics chars = manager.getCameraCharacteristics(camId);
                    Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = camId;
                        frontChars = chars;
                        break;
                    }
                }

                if (frontCameraId == null || frontChars == null) {
                    Log.w(TAG, "Ön kamera bulunamadı.");
                    return;
                }

                // Desteklenen çözünürlüklerden uygun bir boyut seç (örn: 640x480 veya en yakın)
                StreamConfigurationMap map = frontChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) return;
                Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
                Size chosenSize = new Size(640, 480);
                if (jpegSizes != null && jpegSizes.length > 0) {
                    for (Size s : jpegSizes) {
                        if (s.getWidth() <= 1280 && s.getHeight() <= 960) {
                            chosenSize = s;
                            break;
                        }
                    }
                }

                backgroundThread = new HandlerThread("CameraBackgroundThread");
                backgroundThread.start();
                final Handler backgroundHandler = new Handler(backgroundThread.getLooper());

                final ImageReader imageReader = ImageReader.newInstance(chosenSize.getWidth(), chosenSize.getHeight(), ImageFormat.JPEG, 1);
                final HandlerThread finalThread = backgroundThread;

                imageReader.setOnImageAvailableListener(reader -> {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);

                            // Fotoğrafı kaydet
                            File intruderDir = new File(context.getFilesDir(), "intruders");
                            if (!intruderDir.exists()) intruderDir.mkdirs();

                            String logId = UUID.randomUUID().toString();
                            File photoFile = new File(intruderDir, "intruder_" + System.currentTimeMillis() + ".jpg");
                            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                                fos.write(bytes);
                            }

                            // Uygulama adını al
                            String appLabel = targetPkg;
                            try {
                                PackageManager pm = context.getPackageManager();
                                ApplicationInfo info = pm.getApplicationInfo(targetPkg, 0);
                                appLabel = pm.getApplicationLabel(info).toString();
                            } catch (Exception ignored) {}

                            // Günlüğe ekle
                            saveLogEntry(context, new IntruderLog(logId, photoFile.getAbsolutePath(), appLabel, targetPkg, reason, System.currentTimeMillis()));
                            Log.i(TAG, "Casus fotoğrafı başarıyla çekildi ve kaydedildi: " + photoFile.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Fotoğraf kaydetme hatası", e);
                    } finally {
                        if (image != null) image.close();
                        try {
                            reader.close();
                        } catch (Exception ignored) {}
                        if (finalThread != null) {
                            finalThread.quitSafely();
                        }
                    }
                }, backgroundHandler);

                final Integer sensorOrientation = frontChars.get(CameraCharacteristics.SENSOR_ORIENTATION);

                manager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        try {
                            camera.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    try {
                                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                        builder.addTarget(imageReader.getSurface());
                                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                        if (sensorOrientation != null) {
                                            builder.set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation);
                                        }
                                        session.capture(builder.build(), null, backgroundHandler);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Çekim isteği oluşturma hatası", e);
                                        cleanup(camera, imageReader, finalThread);
                                    }
                                }

                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {
                                    cleanup(camera, imageReader, finalThread);
                                }
                            }, backgroundHandler);
                        } catch (Exception e) {
                            Log.e(TAG, "Capture session hatası", e);
                            cleanup(camera, imageReader, finalThread);
                        }
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        cleanup(camera, imageReader, finalThread);
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        cleanup(camera, imageReader, finalThread);
                    }
                }, backgroundHandler);

            } catch (Exception e) {
                Log.e(TAG, "Kamera başlatma hatası", e);
                if (backgroundThread != null) {
                    backgroundThread.quitSafely();
                }
            }
        }).start();
    }

    private static void cleanup(CameraDevice camera, ImageReader reader, HandlerThread thread) {
        try {
            if (camera != null) camera.close();
            if (reader != null) reader.close();
            if (thread != null) thread.quitSafely();
        } catch (Exception ignored) {}
    }

    // ─── Log Yönetimi (JSON) ──────────────────────────────────────────────────

    private static synchronized void saveLogEntry(Context ctx, IntruderLog log) {
        try {
            List<IntruderLog> list = getLogs(ctx);
            list.add(0, log); // En yeni en başta

            JSONArray array = new JSONArray();
            for (IntruderLog item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("photoPath", item.getPhotoPath());
                obj.put("appName", item.getAppName());
                obj.put("packageName", item.getPackageName());
                obj.put("reason", item.getReason());
                obj.put("timestamp", item.getTimestamp());
                array.put(obj);
            }

            File logFile = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                fos.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Log yazma hatası", e);
        }
    }

    public static synchronized List<IntruderLog> getLogs(Context ctx) {
        List<IntruderLog> list = new ArrayList<>();
        File logFile = new File(ctx.getFilesDir(), LOG_FILE_NAME);
        if (!logFile.exists()) return list;

        try (FileInputStream fis = new FileInputStream(logFile)) {
            byte[] bytes = new byte[(int) logFile.length()];
            fis.read(bytes);
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new IntruderLog(
                        obj.getString("id"),
                        obj.getString("photoPath"),
                        obj.getString("appName"),
                        obj.getString("packageName"),
                        obj.getString("reason"),
                        obj.getLong("timestamp")
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "Log okuma hatası", e);
        }
        return list;
    }

    public static synchronized void deleteLog(Context ctx, String logId) {
        try {
            List<IntruderLog> list = getLogs(ctx);
            List<IntruderLog> updated = new ArrayList<>();
            for (IntruderLog item : list) {
                if (item.getId().equals(logId)) {
                    // Dosyayı sil
                    File f = new File(item.getPhotoPath());
                    if (f.exists()) f.delete();
                } else {
                    updated.add(item);
                }
            }

            JSONArray array = new JSONArray();
            for (IntruderLog item : updated) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.getId());
                obj.put("photoPath", item.getPhotoPath());
                obj.put("appName", item.getAppName());
                obj.put("packageName", item.getPackageName());
                obj.put("reason", item.getReason());
                obj.put("timestamp", item.getTimestamp());
                array.put(obj);
            }

            File logFile = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                fos.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Log silme hatası", e);
        }
    }

    public static synchronized void clearAllLogs(Context ctx) {
        try {
            List<IntruderLog> list = getLogs(ctx);
            for (IntruderLog item : list) {
                File f = new File(item.getPhotoPath());
                if (f.exists()) f.delete();
            }
            File logFile = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (logFile.exists()) logFile.delete();
        } catch (Exception e) {
            Log.e(TAG, "Tüm logları silme hatası", e);
        }
    }
}
