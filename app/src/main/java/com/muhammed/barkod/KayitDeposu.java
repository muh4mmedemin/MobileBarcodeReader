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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

        Uri secili = seciliUri(c);
        if (secili != null) {
            try {
                seciliDosyayaEkle(c, secili, satir);
                return null;
            } catch (Exception e) {
                // Kayit kaybolmasin: varsayilan dosyaya yaz ve uyar
                varsayilanaEkle(c, satir);
                indirilenlereKopyala(c);
                return "Seçili dosyaya yazılamadı (" + e.getMessage() + "), otomatik dosyaya kaydedildi";
            }
        }
        varsayilanaEkle(c, satir);
        return indirilenlereKopyala(c);
    }

    private static void varsayilanaEkle(Context c, String satir) throws IOException {
        File ana = anaDosya(c);
        boolean yeni = !ana.exists() || ana.length() == 0;
        try (FileOutputStream out = new FileOutputStream(ana, true)) {
            if (yeni) {
                out.write(BOM);
                out.write(BASLIK.getBytes(StandardCharsets.UTF_8));
            }
            out.write(satir.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------- Kullanicinin sectigi dosya ----------

    /** Kullanici bir dosya sectiyse onun Uri'si, secmediyse null (otomatik dosya kullanilir). */
    public static Uri seciliUri(Context c) {
        String s = prefs(c).getString("seciliDosyaUri", null);
        return s == null ? null : Uri.parse(s);
    }

    public static void seciliDosyaAta(Context c, Uri uri) {
        prefs(c).edit().putString("seciliDosyaUri", uri.toString()).apply();
    }

    public static void seciliDosyaKaldir(Context c) {
        Uri eski = seciliUri(c);
        prefs(c).edit().remove("seciliDosyaUri").apply();
        if (eski != null) {
            try {
                c.getContentResolver().releasePersistableUriPermission(eski,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
    }

    /** Ekranda gosterilecek dosya adi. */
    public static String dosyaAdi(Context c) {
        Uri secili = seciliUri(c);
        if (secili == null) {
            return "İndirilenler / " + DOSYA_ADI + " (otomatik)";
        }
        try (Cursor cur = c.getContentResolver().query(secili,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) {
                return cur.getString(0) + " (seçili)";
            }
        } catch (Exception ignored) {
        }
        return "Seçili dosya";
    }

    private static byte[] uriOku(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("dosya açılamadı");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] tampon = new byte[8192];
            int n;
            while ((n = in.read(tampon)) > 0) {
                bos.write(tampon, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static void uriYaz(Context c, Uri uri, byte[] icerik) throws IOException {
        OutputStream out;
        try {
            out = c.getContentResolver().openOutputStream(uri, "wt");
        } catch (Exception e) {
            out = c.getContentResolver().openOutputStream(uri, "w"); // "wt" desteklemeyen saglayicilar
        }
        if (out == null) {
            throw new IOException("dosyaya yazılamadı");
        }
        try (OutputStream o = out) {
            o.write(icerik);
        }
    }

    /** Secili dosyanin sonuna satir ekler; dosya bossa basligi da yazar. */
    private static void seciliDosyayaEkle(Context c, Uri uri, String satir) throws IOException {
        byte[] mevcut = uriOku(c, uri);
        ByteArrayOutputStream yeni = new ByteArrayOutputStream();
        if (mevcut.length == 0) {
            yeni.write(BOM);
            yeni.write(BASLIK.getBytes(StandardCharsets.UTF_8));
        } else {
            yeni.write(mevcut);
            byte son = mevcut[mevcut.length - 1];
            if (son != '\n' && son != '\r') {
                yeni.write('\n');
            }
        }
        yeni.write(satir.getBytes(StandardCharsets.UTF_8));
        uriYaz(c, uri, yeni.toByteArray());
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
        try {
            byte[] icerik;
            Uri secili = seciliUri(c);
            if (secili != null) {
                icerik = uriOku(c, secili);
            } else {
                File f = anaDosya(c);
                if (!f.exists()) {
                    return satirlar;
                }
                icerik = Files.readAllBytes(f.toPath());
            }
            String metin = new String(icerik, StandardCharsets.UTF_8).replace("\r", "");
            if (metin.startsWith("﻿")) {
                metin = metin.substring(1);
            }
            for (String s : metin.split("\n")) {
                if (!s.trim().isEmpty()) {
                    satirlar.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return satirlar;
    }

    /** Verilen satirlarla dosyayi bastan yazar (son kaydi silmek icin). */
    public static void satirlariYaz(Context c, List<String> satirlar) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String s : satirlar) {
            sb.append(s).append("\n");
        }
        ByteArrayOutputStream icerik = new ByteArrayOutputStream();
        icerik.write(BOM);
        icerik.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        Uri secili = seciliUri(c);
        if (secili != null) {
            uriYaz(c, secili, icerik.toByteArray());
            return;
        }
        try (FileOutputStream out = new FileOutputStream(anaDosya(c), false)) {
            out.write(icerik.toByteArray());
        }
        indirilenlereKopyala(c);
    }
}
