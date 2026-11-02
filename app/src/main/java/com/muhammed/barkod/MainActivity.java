package com.muhammed.barkod;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.File;
import java.io.FileInputStream;
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

public class MainActivity extends Activity {

    private static final String DOSYA_ADI = "barkod_kayitlari.csv";
    private static final String BASLIK = "Tarih;Saat;Bolum;Barkod\n";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private SharedPreferences prefs;
    private RadioGroup bolumGrubu;
    private EditText elleGiris;
    private TextView kayitlarView;
    private TextView dosyaYoluView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("ayarlar", MODE_PRIVATE);
        bolumGrubu = findViewById(R.id.bolumGrubu);
        elleGiris = findViewById(R.id.elleGiris);
        kayitlarView = findViewById(R.id.kayitlar);
        dosyaYoluView = findViewById(R.id.dosyaYolu);

        String kayitliBolum = prefs.getString("bolum", "Kesme");
        bolumGrubu.check("Freze".equals(kayitliBolum) ? R.id.bolumFreze : R.id.bolumKesme);
        bolumGrubu.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putString("bolum", seciliBolum()).apply());

        Button btnTara = findViewById(R.id.btnTara);
        btnTara.setOnClickListener(v -> taramaBaslat());

        Button btnKaydet = findViewById(R.id.btnKaydet);
        btnKaydet.setOnClickListener(v -> elleKaydet());

        // El tipi / bluetooth barkod okuyucular klavye gibi yazip Enter gonderir
        elleGiris.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (enter) {
                elleKaydet();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnPaylas).setOnClickListener(v -> dosyayiPaylas());
        findViewById(R.id.btnSonuSil).setOnClickListener(v -> sonKaydiSilSor());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        dosyaYoluView.setText("Dosya: İndirilenler (Download) / " + DOSYA_ADI);
        listeyiGuncelle();
    }

    private String seciliBolum() {
        return bolumGrubu.getCheckedRadioButtonId() == R.id.bolumFreze ? "Freze" : "Kesme";
    }

    private void taramaBaslat() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Bölüm: " + seciliBolum() + " - Barkodu okutun");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult sonuc = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (sonuc != null) {
            if (sonuc.getContents() != null) {
                kaydet(sonuc.getContents());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void elleKaydet() {
        String metin = elleGiris.getText().toString().trim();
        if (metin.isEmpty()) {
            return;
        }
        kaydet(metin);
        elleGiris.setText("");
    }

    private void kaydet(String barkod) {
        barkod = barkod.trim();
        if (barkod.isEmpty()) {
            return;
        }
        Date simdi = new Date();
        String tarih = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(simdi);
        String saat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(simdi);
        String bolum = seciliBolum();
        String satir = tarih + ";" + saat + ";" + bolum + ";" + csvAlan(barkod) + "\n";

        try {
            File ana = anaDosya();
            boolean yeni = !ana.exists() || ana.length() == 0;
            try (FileOutputStream out = new FileOutputStream(ana, true)) {
                if (yeni) {
                    out.write(BOM);
                    out.write(BASLIK.getBytes(StandardCharsets.UTF_8));
                }
                out.write(satir.getBytes(StandardCharsets.UTF_8));
            }
            String hata = indirilenlereKopyala();
            if (hata == null) {
                Toast.makeText(this, "Kaydedildi: " + bolum + " - " + barkod, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kaydedildi (uygulama klasörüne). İndirilenler hatası: " + hata,
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "KAYIT HATASI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        listeyiGuncelle();
    }

    private static String csvAlan(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }
        return s;
    }

    /** Asil kayit dosyasi: uygulamanin kendi klasorunde (Android/data/com.muhammed.barkod/files). */
    private File anaDosya() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        return new File(dir, DOSYA_ADI);
    }

    /** Ana dosyanin tamamini Indirilenler/barkod_kayitlari.csv uzerine yazar. Hata yoksa null doner. */
    private String indirilenlereKopyala() {
        try {
            byte[] icerik = Files.readAllBytes(anaDosya().toPath());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri uri = indirilenlerUri(true);
                if (uri == null) {
                    return "dosya oluşturulamadı";
                }
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
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
    private Uri indirilenlerUri(boolean olustur) {
        ContentResolver cr = getContentResolver();
        String kayitli = prefs.getString("indirilenlerUri", null);
        if (kayitli != null) {
            Uri uri = Uri.parse(kayitli);
            try (Cursor c = cr.query(uri, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    return uri;
                }
            } catch (Exception ignored) {
            }
        }
        Uri koleksiyon = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        try (Cursor c = cr.query(koleksiyon, new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{DOSYA_ADI}, null)) {
            if (c != null && c.moveToFirst()) {
                Uri uri = Uri.withAppendedPath(koleksiyon, String.valueOf(c.getLong(0)));
                prefs.edit().putString("indirilenlerUri", uri.toString()).apply();
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
            prefs.edit().putString("indirilenlerUri", uri.toString()).apply();
        }
        return uri;
    }

    private List<String> satirlariOku() {
        List<String> satirlar = new ArrayList<>();
        File f = anaDosya();
        if (!f.exists()) {
            return satirlar;
        }
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int okunan = 0;
            while (okunan < b.length) {
                int n = in.read(b, okunan, b.length - okunan);
                if (n < 0) break;
                okunan += n;
            }
            String metin = new String(b, 0, okunan, StandardCharsets.UTF_8);
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

    private void listeyiGuncelle() {
        List<String> satirlar = satirlariOku();
        int kayitSayisi = Math.max(0, satirlar.size() - 1); // baslik haric
        StringBuilder sb = new StringBuilder();
        sb.append("Toplam kayıt: ").append(kayitSayisi).append("\n\n");
        int sinir = 0;
        for (int i = satirlar.size() - 1; i >= 1 && sinir < 100; i--, sinir++) {
            sb.append(satirlar.get(i).replace(";", "  ")).append("\n");
        }
        kayitlarView.setText(sb.toString());
    }

    private void sonKaydiSilSor() {
        List<String> satirlar = satirlariOku();
        if (satirlar.size() <= 1) {
            Toast.makeText(this, "Silinecek kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        String son = satirlar.get(satirlar.size() - 1);
        new AlertDialog.Builder(this)
                .setTitle("Son kayıt silinsin mi?")
                .setMessage(son.replace(";", "  "))
                .setPositiveButton("Sil", (d, w) -> sonKaydiSil(satirlar))
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void sonKaydiSil(List<String> satirlar) {
        satirlar.remove(satirlar.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String s : satirlar) {
            sb.append(s).append("\n");
        }
        try (FileOutputStream out = new FileOutputStream(anaDosya(), false)) {
            out.write(BOM);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Toast.makeText(this, "Silme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        indirilenlereKopyala();
        Toast.makeText(this, "Son kayıt silindi", Toast.LENGTH_SHORT).show();
        listeyiGuncelle();
    }

    private void dosyayiPaylas() {
        if (!anaDosya().exists()) {
            Toast.makeText(this, "Henüz kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            indirilenlereKopyala();
            uri = indirilenlerUri(false);
            if (uri == null) {
                Toast.makeText(this, "Dosya bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Kisisel kullanim: FileProvider yerine file:// paylasimina izin ver
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
            uri = Uri.fromFile(anaDosya());
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Barkod kayıtlarını paylaş"));
    }
}
