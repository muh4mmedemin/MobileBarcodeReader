package com.muhammed.barkod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Cokmeleri dosyaya yazar ve bir sonraki acilista gosterir. */
public final class HataKaydi {

    private static boolean kuruldu;

    private HataKaydi() {
    }

    private static File dosya(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) {
            dir = c.getFilesDir();
        }
        return new File(dir, "hata.txt");
    }

    public static void kur(Context c) {
        if (kuruldu) {
            return;
        }
        kuruldu = true;
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler onceki = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            yaz(app, e);
            if (onceki != null) {
                onceki.uncaughtException(thread, e);
            }
        });
    }

    public static void yaz(Context c, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String zaman = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
            String metin = "[" + zaman + "]\n" + sw + "\n";
            try (FileOutputStream out = new FileOutputStream(dosya(c), true)) {
                out.write(metin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void varsaGoster(Activity a) {
        File f = dosya(a);
        if (!f.exists() || f.length() == 0) {
            return;
        }
        String metin;
        try {
            metin = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }
        if (metin.length() > 4000) {
            metin = metin.substring(metin.length() - 4000);
        }
        final String gosterilecek = metin;
        new AlertDialog.Builder(a)
                .setTitle("Önceki hata kaydı")
                .setMessage(gosterilecek)
                .setPositiveButton("Sil", (d, w) -> f.delete())
                .setNegativeButton("Kapat", null)
                .show();
    }
}
