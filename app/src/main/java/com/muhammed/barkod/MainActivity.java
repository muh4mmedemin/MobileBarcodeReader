package com.muhammed.barkod;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private RadioGroup bolumGrubu;
    private EditText elleGiris;
    private TextView kayitlarView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HataKaydi.kur(this);
        setContentView(R.layout.activity_main);
        try {
            String surum = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            setTitle("Barkod Okuyucu  v" + surum);
        } catch (Exception ignored) {
        }
        HataKaydi.varsaGoster(this);

        prefs = getSharedPreferences("ayarlar", MODE_PRIVATE);
        bolumGrubu = findViewById(R.id.bolumGrubu);
        elleGiris = findViewById(R.id.elleGiris);
        kayitlarView = findViewById(R.id.kayitlar);
        TextView dosyaYoluView = findViewById(R.id.dosyaYolu);

        String kayitliBolum = prefs.getString("bolum", "CNC");
        int secilecek = R.id.bolumCnc;
        for (int i = 0; i < bolumGrubu.getChildCount(); i++) {
            View v = bolumGrubu.getChildAt(i);
            if (v instanceof RadioButton && ((RadioButton) v).getText().toString().equals(kayitliBolum)) {
                secilecek = v.getId();
            }
        }
        bolumGrubu.check(secilecek);
        bolumGrubu.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putString("bolum", seciliBolum()).apply());

        findViewById(R.id.btnTara).setOnClickListener(v -> taramaBaslat());
        findViewById(R.id.btnKaydet).setOnClickListener(v -> elleKaydet());

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

        dosyaYoluView.setText("Dosya: İndirilenler (Download) / " + KayitDeposu.DOSYA_ADI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        listeyiGuncelle(); // seri taramadan donunce listeyi yenile
    }

    private String seciliBolum() {
        RadioButton secili = findViewById(bolumGrubu.getCheckedRadioButtonId());
        return secili != null ? secili.getText().toString() : "CNC";
    }

    private void taramaBaslat() {
        Intent i = new Intent(this, SeriTaramaActivity.class);
        i.putExtra(SeriTaramaActivity.EXTRA_BOLUM, seciliBolum());
        startActivity(i);
    }

    private void elleKaydet() {
        String barkod = elleGiris.getText().toString().trim();
        if (barkod.isEmpty()) {
            return;
        }
        String bolum = seciliBolum();
        try {
            String hata = KayitDeposu.kaydet(this, bolum, barkod);
            if (hata == null) {
                Toast.makeText(this, "Kaydedildi: " + bolum + " - " + barkod, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kaydedildi (uygulama klasörüne). İndirilenler hatası: " + hata,
                        Toast.LENGTH_LONG).show();
            }
            elleGiris.setText("");
        } catch (IOException e) {
            Toast.makeText(this, "KAYIT HATASI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        listeyiGuncelle();
    }

    private void listeyiGuncelle() {
        List<String> satirlar = KayitDeposu.satirlariOku(this);
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
        List<String> satirlar = KayitDeposu.satirlariOku(this);
        if (satirlar.size() <= 1) {
            Toast.makeText(this, "Silinecek kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        String son = satirlar.get(satirlar.size() - 1);
        new AlertDialog.Builder(this)
                .setTitle("Son kayıt silinsin mi?")
                .setMessage(son.replace(";", "  "))
                .setPositiveButton("Sil", (d, w) -> {
                    satirlar.remove(satirlar.size() - 1);
                    try {
                        KayitDeposu.satirlariYaz(this, satirlar);
                        Toast.makeText(this, "Son kayıt silindi", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(this, "Silme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    listeyiGuncelle();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void dosyayiPaylas() {
        if (!KayitDeposu.anaDosya(this).exists()) {
            Toast.makeText(this, "Henüz kayıt yok", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            KayitDeposu.indirilenlereKopyala(this);
            uri = KayitDeposu.indirilenlerUri(this, false);
            if (uri == null) {
                Toast.makeText(this, "Dosya bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Kisisel kullanim: FileProvider yerine file:// paylasimina izin ver
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
            uri = Uri.fromFile(KayitDeposu.anaDosya(this));
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Barkod kayıtlarını paylaş"));
    }
}
