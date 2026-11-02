package com.muhammed.barkod;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/** Kamera acik kalir; her okunan barkod aninda kaydedilir, "Bitir"e basilana kadar okumaya devam eder. */
public class SeriTaramaActivity extends Activity {

    public static final String EXTRA_BOLUM = "bolum";
    /** Ayni barkod bu sure icinde tekrar okunursa yeniden kaydedilmez (kamera ayni kodu art arda gorur). */
    private static final long TEKRAR_SURESI_MS = 3000;
    private static final int KAMERA_IZNI = 10;

    private DecoratedBarcodeView barkodView;
    private BeepManager beepManager;
    private TextView durumView;
    private String bolum;
    private String sonBarkod;
    private long sonZaman;
    private int buOturumdaSayi;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            String barkod = result.getText();
            if (barkod == null) {
                return;
            }
            barkod = barkod.trim();
            if (barkod.isEmpty()) {
                return;
            }
            long simdi = System.currentTimeMillis();
            if (barkod.equals(sonBarkod) && simdi - sonZaman < TEKRAR_SURESI_MS) {
                sonZaman = simdi; // barkod hala kamerada; sure uzasin
                return;
            }
            sonBarkod = barkod;
            sonZaman = simdi;
            try {
                barkodKaydet(barkod);
            } catch (Throwable t) {
                HataKaydi.yaz(SeriTaramaActivity.this, t);
                durumView.setText("HATA: " + t);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_seri_tarama);

        bolum = getIntent().getStringExtra(EXTRA_BOLUM);
        if (bolum == null) {
            bolum = "CNC";
        }

        barkodView = findViewById(R.id.barkodView);
        durumView = findViewById(R.id.durum);
        Button btnBitir = findViewById(R.id.btnBitir);
        btnBitir.setOnClickListener(v -> finish());

        barkodView.setStatusText("Bölüm: " + bolum + "  -  Barkodları sırayla okutun");
        beepManager = new BeepManager(this);
        beepManager.setVibrateEnabled(true);
        durumView.setText("Bölüm: " + bolum + "\nOkunan: 0");

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, KAMERA_IZNI);
        }
        barkodView.decodeContinuous(callback);
    }

    private void barkodKaydet(String barkod) {
        try {
            String hata = KayitDeposu.kaydet(this, bolum, barkod);
            buOturumdaSayi++;
            try {
                beepManager.playBeepSoundAndVibrate();
            } catch (Throwable t) {
                HataKaydi.yaz(this, t);
            }
            durumView.setBackgroundColor(Color.argb(200, 0, 120, 0));
            String metin = "✔ " + barkod + "\nBölüm: " + bolum + "   Okunan: " + buOturumdaSayi;
            if (hata != null) {
                metin += "\n(İndirilenler kopyası hatası: " + hata + ")";
            }
            durumView.setText(metin);
        } catch (Exception e) {
            durumView.setBackgroundColor(Color.argb(200, 160, 0, 0));
            durumView.setText("KAYIT HATASI: " + e.getMessage());
            Toast.makeText(this, "KAYIT HATASI: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == KAMERA_IZNI) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                barkodView.resume();
            } else {
                Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barkodView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barkodView.pause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barkodView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }
}
