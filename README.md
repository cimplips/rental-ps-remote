# RentalPSRemote

MVP sederhana untuk remote Android TV dari HP melalui Wi‑Fi lokal.

## Fitur MVP
- HP Admin mengontrol TV lewat HTTP lokal.
- Mulai sesi, tambah waktu, akhiri sesi.
- TV tidak dimatikan.
- 5 menit terakhir: countdown kecil/transparan di kanan bawah.
- Waktu habis: TV menampilkan blank screen/tagihan.
- Tidak membutuhkan internet setelah APK terpasang; HP dan TV cukup satu jaringan Wi‑Fi.

## Struktur
- `admin/` — aplikasi Android HP admin.
- `tv/` — aplikasi Android TV.
- `.github/workflows/build.yml` — build APK otomatis di GitHub Actions.

## Cara uji
1. Install APK `tv-debug.apk` di Android TV dan buka aplikasinya.
2. Pastikan HP dan TV berada di Wi‑Fi yang sama.
3. Lihat IP TV dari jaringan/router (versi berikutnya akan menampilkan IP langsung di layar TV).
4. Masukkan IP TV ke aplikasi Admin.
5. Tekan **Tes Koneksi**, lalu **Mulai 1 Jam**.

> MVP ini menggunakan port TCP 8787. Pastikan jaringan lokal mengizinkan komunikasi antar perangkat.
