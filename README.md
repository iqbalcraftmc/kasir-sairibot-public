SairiBot Kasir

SairiBot Kasir adalah aplikasi kasir berbasis Android yang dirancang khusus untuk mempermudah dan mempercepat transaksi penjualan harian Anda, dilengkapi dengan fitur pembuatan QRIS otomatis.

Fitur Utama

- Transaksi QRIS Otomatis: Integrasi untuk pembuatan QRIS langsung dalam aplikasi, memudahkan pembayaran non-tunai.
- Manajemen Transaksi: Mencatat dan mengelola penjualan secara rapi dan efisien.

Cara Mendapatkan Token Lisensi & API Key

Versi publik aplikasi ini memerlukan token lisensi dan API Key SairiBot agar dapat berfungsi.

1. Kunjungi halaman resmi "SairiBot API" (https://api.sairibot.my.id/profile) untuk membuat/mengakses akun dan mendapatkan API Key milik Anda sendiri.
2. Kunjungi "website pengembang" (https://iqbalcraftmc.github.io/) untuk menghubungi saya dan mendapatkan token lisensi akses aplikasi.

«⚠️ Penting: Setiap pengguna wajib menggunakan API Key dari akun SairiBot miliknya sendiri. Jangan menggunakan API Key milik pengembang untuk transaksi Anda.»

Cara Mengatur Token & API Key

Setelah mendapatkan data tersebut, ikuti langkah-langkah berikut untuk mengaturnya:

1. Buka aplikasi SairiBot Kasir di perangkat Android Anda.
2. Masuk ke menu Pengaturan (Settings) atau konfigurasi aplikasi.
3. Masukkan token lisensi serta API Key SairiBot milik Anda sendiri pada kolom yang telah disediakan.
4. Simpan pengaturan agar aplikasi siap digunakan untuk bertransaksi.

Templat Pembayaran Jarak Jauh (Bayar)

Bagi yang membutuhkan sistem pembayaran jarak jauh, Anda dapat menggunakan templat web pendukung di "bayar.github.io" (https://github.com/iqbalcraftmc/bayar.github.io).

Templat tersebut bersifat Open Source dan dapat disesuaikan dengan kebutuhan Anda.

🔑 Konfigurasi API Key pada Templat

Jika Anda menyalin atau melakukan fork templat pembayaran tersebut untuk penggunaan Anda sendiri, ganti API Key yang ditandai di dalam kode dengan API Key SairiBot milik Anda sendiri.

Pada kode JavaScript, cari bagian yang memiliki penanda:

// ⚠️ OPEN SOURCE - GANTI API KEY DENGAN API KEY ANDA SENDIRI

Kemudian ganti API Key pada baris "fetch()" dengan API Key dari akun SairiBot Anda.

«Catatan: API Key terhubung dengan akun SairiBot yang menggunakannya. Gunakan API Key akun Anda sendiri agar transaksi dan pemeriksaan status pembayaran menggunakan akun Anda.»

«🔒 Keamanan: Jangan mempublikasikan API Key pribadi Anda di repository GitHub public. Jika memungkinkan, gunakan konfigurasi yang tidak mengekspos API Key secara langsung pada source code yang dapat dilihat umum.»

Pengembang & Hak Cipta

Aplikasi ini dirancang, dibuat, dan dikembangkan sepenuhnya oleh iqbalcraftmc:

- Website: "iqbalcraftmc.github.io" (https://iqbalcraftmc.github.io/)
- GitHub: "github.com/iqbalcraftmc" (https://github.com/iqbalcraftmc)

Copyright © 2026 iqbalcraftmc. All rights reserved.

Lisensi

Proyek ini dilindungi di bawah Lisensi MIT. Silakan lihat file "LICENSE" (LICENSE) untuk informasi lebih lanjut.
