# SairiBot Kasir

SairiBot Kasir adalah aplikasi kasir berbasis Android yang dirancang khusus untuk mempermudah dan mempercepat transaksi penjualan harian Anda, dilengkapi dengan fitur pembuatan QRIS otomatis.

# Fitur Utama

- Transaksi QRIS Otomatis: Integrasi untuk pembuatan QRIS langsung dalam aplikasi, memudahkan pembayaran non-tunai.
- Manajemen Transaksi: Mencatat dan mengelola penjualan secara rapi dan efisien.

# Cara Mendapatkan Token Lisensi & API Key

Versi publik aplikasi ini memerlukan token lisensi dan API Key SairiBot agar dapat berfungsi.

1. Kunjungi halaman resmi SairiBot API https://api.sairibot.my.id/profile untuk membuat/mengakses akun dan mendapatkan API Key milik Anda sendiri.
2. Kunjungi website pengembang https://iqbalcraftmc.github.io/ untuk menghubungi saya dan mendapatkan token lisensi akses aplikasi.

«⚠️ Penting: Setiap pengguna wajib menggunakan API Key dari akun SairiBot miliknya sendiri. Jangan menggunakan API Key milik pengembang untuk transaksi Anda.»

# Cara Mengatur Token & API Key

Setelah mendapatkan data tersebut, ikuti langkah-langkah berikut untuk mengaturnya:

1. Buka aplikasi SairiBot Kasir di perangkat Android Anda.
2. Masuk ke menu Pengaturan (Settings) atau konfigurasi aplikasi.
3. Masukkan token lisensi serta API Key SairiBot milik Anda sendiri pada kolom yang telah disediakan.
4. Simpan pengaturan agar aplikasi siap digunakan untuk bertransaksi.

# 🌐 Templat Pembayaran Jarak Jauh (Self-Hosted)

Aplikasi SairiBot Kasir mendukung sistem pembayaran jarak jauh melalui web templat yang disetup secara mandiri oleh masing-masing pengguna. Hal ini bertujuan agar penggunaan API Key dan kuota transaksi terpisah secara independen.

Anda dapat menggunakan dan mem-fork templat web resmi kami dari repositori GitHub:
👉 [bayar.github.io](https://github.com/iqbalcraftmc/bayar.github.io)

---

## 🚀 Pilihan Layanan Hosting (Deployment)

Anda bebas mengunggah (*deploy*) templat tersebut ke beberapa platform hosting gratis berikut:

1. **Cloudflare Pages (Sangat Direkomendasikan)**
   - **Kelebihan**: Loading super cepat, dukungan *Custom Domain* gratis (`.pages.dev`), dan manajemen *environment variable* yang lebih aman.
   - **Cara Setup**: 
     1. Login ke [Cloudflare Dashboard](https://dash.cloudflare.com/).
     2. Masuk ke menu **Workers & Pages** > **Create application** > **Pages** > **Connect to Git**.
     3. Pilih repositori `bayar.github.io` hasil fork Anda lalu tekan **Save and Deploy**.

2. **GitHub Pages**
   - **Cara Setup**: Masuk ke repositori hasil fork Anda di GitHub > **Settings** > **Pages** > Pada bagian *Source*, pilih cabang `main` lalu simpan.

3. **Custom Domain Pribadi**
   - Jika Anda memiliki domain sendiri (contoh: `https://kasir.tokoanda.com`), Anda dapat menghubungkannya ke Cloudflare Pages atau GitHub Pages di atas.

---

## 🔑 Konfigurasi API Key & URL Kustom

1. Buka berkas `index.html` atau skrip konfigurasi pada templat web Anda.
2. Cari bagian berikut dan ganti dengan **API Key SairiBot milik Anda sendiri**:
   ```javascript
   // ⚠️ GANTI DENGAN API KEY SAIRIBOT ANDA
   const API_KEY = "API_KEY_ANDA_DI_SINI";
   
Kemudian ganti API Key pada baris "fetch()" dengan API Key dari akun SairiBot Anda.

«Catatan: API Key terhubung dengan akun SairiBot yang menggunakannya. Gunakan API Key akun Anda sendiri agar transaksi dan pemeriksaan status pembayaran menggunakan akun Anda.»

«🔒 Keamanan: Jangan mempublikasikan API Key pribadi Anda di repository GitHub public. Jika memungkinkan, gunakan konfigurasi yang tidak mengekspos API Key secara langsung pada source code yang dapat dilihat umum.»

# Pengembang & Hak Cipta

Aplikasi ini dirancang, dibuat, dan dikembangkan sepenuhnya oleh iqbalcraftmc:

- Website: "iqbalcraftmc.github.io" https://iqbalcraftmc.github.io/
- GitHub: "github.com/iqbalcraftmc" https://github.com/iqbalcraftmc

Copyright © 2026 iqbalcraftmc. All rights reserved.

Lisensi

Proyek ini dilindungi di bawah Lisensi MIT. Silakan lihat file "LICENSE" (LICENSE) untuk informasi lebih lanjut.
