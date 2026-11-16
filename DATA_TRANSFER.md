# OneOS Data Transfer

One UI (host) → OxygenOS 16 (OneOS guest) veri aktarımı.

Bu özellik [OneOS](https://github.com/fahadalijaved/OneOS) üzerine eklenmiştir.
Mevcut OxygenOS 16 kurulumu korunur: `system.raw` silinmez, `remove` / `purge` / destructive `begin` çalıştırılmaz.

## Ne aktarılır

| Kaynak (One UI) | Hedef (OxygenOS 16) |
|-----------------|---------------------|
| Fotoğraflar | `Pictures/OneOS Transfer` |
| Videolar | `Movies/OneOS Transfer` |
| Belgeler | `Documents/OneOS Transfer` |
| İndirilenler | `Download/OneOS Transfer` |
| Kişiler | Rehber (vCard + otomatik içe aktarma) |
| Uygulamalar | Bring Apps (`bring-collect`: APK + veri) |

## Nasıl çalışır

1. One UI’de OneOS → **Veri Aktarımı** ekranını aç.
2. Kategori seç, dosya/kişi/uygulama seç.
3. Yinelenen dosya: **Atla** (varsayılan) veya **Yeniden adlandır**.
4. **Aktarımı Başlat**.
5. Dosyalar yalnızca `/data/local/tmp/oneos/data/media/0/` altına yazılır.
6. OxygenOS 16 açıksa medya taraması ve kişi içe aktarma hemen çalışır.
7. Kapalıysa bir sonraki OxygenOS 16 açılışında tamamlanır.

## Güvenlik

- Host kaynakları salt okunur.
- Yazma yalnızca OneOS stage altında.
- SHA-256 doğrulama (`transfer-place`).
- Path traversal ve symlink reddi.
- Mevcut dosyanın üzerine otomatik yazılmaz.
- Host MediaStore’a Guest dosyası eklenmez.

## Derleme

Gereken: JDK 21, Android SDK.

```bash
cd app-android
./gradlew :app:assembleFullDebug
```

APK: `app-android/app/build/outputs/apk/full/debug/`

GitHub Actions: `.github/workflows/build-apk.yml`  
Push veya **Actions → Build OneOS APK → Run workflow**.

## Durum

- Dosya aktarımı: kod tamam
- Galeri tarama: Guest tarafında (`cmd media scan` + açılış hook)
- Kişiler: vCard + Guest `content insert`
- Uygulama verisi: mevcut Bring Apps
- Gerçek cihaz testi: kullanıcı tarafında

Deneyseldir. Yedek almadan deneme.
