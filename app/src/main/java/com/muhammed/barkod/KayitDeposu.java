package com.muhammed.barkod;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Barkod kayitlarinin CSV dosyasina yazilmasi / okunmasi. */
public final class KayitDeposu {

    public static final String DOSYA_ADI = "barkod_kayitlari.csv";
    private static final String BASLIK = "Tarih;Saat;Bolum;Barkod\n";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private KayitDeposu() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    /**
     * Barkodu ana dosyaya ekler ve Indirilenler kopyasini gunceller.
     * Basariliysa null, ana dosyaya yazilamadiysa IOException firlatir;
     * yalnizca Indirilenler kopyasi basarisizsa hata mesajini dondurur.
     */
    public static String kaydet(Context c, String bolum, String barkod) throws IOException {
        Date simdi = new Date();
        String tarih = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(simdi);
        String saat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(simdi);
        String satir = tarih + ";" + saat + ";" + bolum + ";" + csvAlan(barkod) + "\n";

        File ana = anaDosya(c);
        boolean yeni = !ana.exists() || ana.length() == 0;
        try (FileOutputStream out = new FileOutputStream(ana, true)) {
            if (yeni) {
                out.write(BOM);
                out.write(BASLIK.getBytes(StandardCharsets.UTF_8));
            }
            out.write(satir.getBytes(StandardCharsets.UTF_8));
        }
        return indirilenlereKopyala(c);
    }

    private static String csvAlan(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }
        return s;
    }

    /** Asil kayit dosyasi: Android/data/com.muhammed.barkod/files/barkod_kayitlari.csv */
    public static File anaDosya(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) {
            dir = c.getFilesDir();
        }
        return new File(dir, DOSYA_ADI);
    }

    /** Ana dosyanin tamamini Indirilenler/barkod_kayitlari.csv uzerine yazar. Hata yoksa null doner. */
    public static String indirilenlereKopyala(Context c) {
        try {
            byte[] icerik = Files.readAllBytes(anaDosya(c).toPath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri uri = indirilenlerUri(c, true);
                if (uri == null) {
                    return "dosya oluşturulamadı";
                }
                try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) {
                        return "dosya açılamadı";
                    }
                    out.write(icerik);
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                try (FileOutputStream out = new FileOutputStream(new File(dir, DOSYA_ADI), false)) {
                    out.write(icerik);
                }
            }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /** Android 10+ icin Indirilenler'deki dosyanin Uri'si; yoksa (olustur=true ise) olusturur. */
    public static Uri indirilenlerUri(Context c, boolean olustur) {
        ContentResolver cr = c.getContentResolver();
        SharedPreferences p = prefs(c);
        String kayitli = p.getString("indirilenlerUri", null);
        if (kayitli != null) {
            Uri uri = Uri.parse(kayitli);
            try (Cursor cur = cr.query(uri, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    return uri;
                }
            } catch (Exception ignored) {
            }
        }
        Uri koleksiyon = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        try (Cursor cur = cr.query(koleksiyon, new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{DOSYA_ADI}, null)) {
            if (cur != null && cur.moveToFirst()) {
                Uri uri = Uri.withAppendedPath(koleksiyon, String.valueOf(cur.getLong(0)));
                p.edit().putString("indirilenlerUri", uri.toString()).apply();
                return uri;
            }
        } catch (Exception ignored) {
        }
        if (!olustur) {
            return null;
        }
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, DOSYA_ADI);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = cr.insert(koleksiyon, cv);
        if (uri != null) {
            p.edit().putString("indirilenlerUri", uri.toString()).apply();
        }
        return uri;
    }

    /** Dosyadaki tum satirlar (ilk satir baslik). */
    public static List<String> satirlariOku(Context c) {
        List<String> satirlar = new ArrayList<>();
        File f = anaDosya(c);
        if (!f.exists()) {
            return satirlar;
        }
        try {
            String metin = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (metin.startsWith("﻿")) {
                metin = metin.substring(1);
            }
            for (String s : metin.split("\n")) {
                if (!s.trim().isEmpty()) {
                    satirlar.add(s);
                }
            }
        } catch (IOException ignored) {
        }
        return satirlar;
    }

    /** Verilen satirlarla dosyayi bastan yazar (son kaydi silmek icin). */
    public static void satirlariYaz(Context c, List<String> satirlar) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String s : satirlar) {
            sb.append(s).append("\n");
        }
        try (FileOutputStream out = new FileOutputStream(anaDosya(c), false)) {
            out.write(BOM);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        indirilenlereKopyala(c);
    }
}
