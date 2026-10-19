# Visual Novel — libGDX Engine Sample

Contoh visual novel lengkap berbahasa Indonesia yang dibangun dengan **libGDX 1.12.1**,
full offline, tanpa engine pihak ketiga. Dibuat sebagai sampel: engine + naskah + aset,
semuanya dari repo ini.

## Fitur engine

- **Naskah format `.vn`** — format teks readable (label / jump / choice / set-flag / if),
  di-parse saat runtime (`Script.java`).
- **Typewriter effect** — ketuk untuk menyelesaikan kalimat, ketuk lagi untuk lanjut.
- **Portrait karakter** — muncul saat karakter bicara, ganti ekspresi mengikuti naskah
  (`Nama[ekspresi]: "..."`); fallback otomatis ke ekspresi normal bila file tak ada.
- **Style visual per scene** — `style: normal|memory|crisis` memberi tint naratif
  (masa kini / kenangan / krisis) ke background + portrait.
- **Pilihan 2–3 opsi** dengan kondisi tampil (`[if: flag>=3]`) dan efek samping (`[set: f+1]`).
- **Branching nyata** — flags/variabel; pilihan awal bisa mengunci cabang di akhir.
- **Galeri ending** — ending yang terbuka tampil judulnya, yang terkunci tampil `???`.
- **Auto-save per scene** + tombol Lanjutkan.
- **BGM + SFX** — loop dan one-shot, semua lokal.
- **Transisi fade** antar background.

## Format naskah (cerita.vn)

```
label: prolog
bg: studio_malam.png
style: normal
bgm: studio_pad
sfx: jingle
Bagas[tegang]: "Dialog di sini..."
*Ini narasi, tanpa pembicara.*
choice:
  - "Tanya soal suara itu" -> tanya_suara [set: penasaran=1]
  - "Pura-pura tidak dengar" -> diam_saja [if: berani>=2]
jump: label_lain
set: dukungan+1
if: yoga=0 -> final_lawan | else -> final_tanda
ending: lentera_menyala "Lentera Tetap Menyala"
```

- Kondisi memakai operator `=`, `!=`, `>=`, `<=`; flag default 0.
- `set:` mendukung `=`, `+`, `-`.
- Portrait dicari di `assets/portrait/{nama}_{ekspresi}.png`
  (nama = kata pertama speaker, lowercase).

## Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI GitHub Actions membangun otomatis tiap push ke `main`.

## Aset

- **Background & portrait** — dibuat dengan image generation untuk sampel ini,
  satu gaya painterly yang konsisten; varian gaya (memory/crisis) diberi alasan naratif.
- **Font** — Merriweather (SIL Open Font License 1.1).
- **Audio** — disintesis prosedural khusus untuk proyek ini (BGM + SFX), original.

## Struktur

```
app/src/main/
  java/com/hozinking/visualnovel/
    AndroidLauncher.java  VnGame.java        StoryScreen.java
    TitleScreen.java      GalleryScreen.java Script.java
    SaveManager.java      AudioMan.java
  assets/
    cerita.vn             bg/  portrait/  audio/  font/
```

Paket: `com.hozinking.visualnovel` — Lisensi: MIT.
