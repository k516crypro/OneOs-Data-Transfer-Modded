# OneOS — OxygenOS 16 Guest için Android yardımcı uygulaması (modlu)

OneOS, desteklenen Android telefonlarda One UI ana sisteminden ayrı bir Android konuk sistemini hazırlamak ve başlatmak için kullanılan Android uygulamasıdır. Sistem imajı telefona klasik bir ROM gibi flaşlanmaz; uygulama root üzerinden OneOS stage alanını yönetir, uygunluk kontrolleri yapar ve konuk sistemle ana sistem arasında geçiş akışını başlatır. Bu depo uygulamanın kendisini içerir; OxygenOS imajı değildir.

Bu depo, [OneOS projesi](https://github.com/fahadalijaved/OneOS) üzerine kullanıcı talebiyle hazırlanmış modlu bir derlemedir. Android uygulama kimliği `com.fahadali.oneos`; kaynakta tanımlı sürüm `0.5.17` (versionCode `146`). Bu modlu derlemede aşağıdaki eklemeler ve düzeltmeler bulunur.

## Bu modlu sürümde neler değişti?

- **One UI → OxygenOS 16 Guest aktarımı:** Fotoğraf, video, belge ve indirilen dosyalar aktarılabilir. Kişiler vCard olarak hazırlanır. Uygulama seçimi mevcut Bring Apps aktarım akışını kullanır.
- **Güvenli dosya aktarımı:** One UI kaynakları salt okunur kullanılır; hedef yalnızca OneOS Guest stage altındaki medya alanıdır. Dosya adı/yol güvenlik kontrolleri ve SHA-256 doğrulaması vardır. Boş alan uygunsa kontrol edilir. Yinelenen dosyalar varsayılan olarak atlanır veya yeniden adlandırılır; güvenli üzerine yazma uygulanmadığından Replace seçeneği devre dışıdır.
- **UI Activator başlatma düzeltmesi:** Uzun activator betiği artık tek bir `sh -c` argümanı olarak verilmek yerine runtime betik dosyasına yazılıp dosyadan çalıştırılır. Bu değişiklik, loglarda görülen `Argument list too long` hatasına neden olan uzun argüman yolunu kaldırır. Gerçek cihazda doğrulama henüz yapılmadı.
- **GitHub Actions derlemesi:** Guest helper APK ve full debug APK otomatik derlenir; build çıktısı Actions artifact’i olarak sunulur. GitHub Packages erişim bilgileri yalnızca Actions secrets üzerinden kullanılır.

Aktarımın ayrıntıları için [`DATA_TRANSFER.md`](DATA_TRANSFER.md) dosyasına bakın.

## APK’yı edinme ve kurma

[OneOS Modded 0.5.17 debug pre-release](https://github.com/k516crypro/OneOs-Data-Transfer-Modded/releases/tag/oneos-modded-0.5.17-debug) sayfasından `OneOS-Modded-0.5.17-debug.apk` dosyasını indirin. Android’de indirdiğiniz APK’yı açın ve istenirse kullandığınız dosya yöneticisi/tarayıcı için “Bu kaynaktan yüklemeye izin ver” seçeneğini etkinleştirin.

**İmza uyarısı:** Yayınlanan dosya CI tarafından üretilmiş bir **debug APK**’dır. Mevcut OneOS release APK’sının imza anahtarıyla aynı olmayabilir ve onun üzerine güncelleme olarak kurulamayabilir. Sırf denemek için mevcut uygulamayı kaldırmayın; kaldırma uygulama verilerini silebilir ve root iznini yeniden vermenizi gerektirebilir. Önce yedek alın.

## Derleme

Gerekenler: JDK 21 ve Android SDK API 37. Depo kök dizininden:

```bash
./scripts/build-guest-manager.sh debug
cd app-android
./gradlew :app:assembleFullDebug --stacktrace
```

Full debug APK çıktısı: `app-android/app/build/outputs/apk/full/debug/app-full-debug.apk`.

## GitHub Actions

`main` dalına push edildiğinde **Build OneOS APK** workflow’u çalışır. Tamamlanan run’ın Artifacts bölümünde `oneos-full-debug` bulunur. Bu artifact 14 gün saklanır. Samsung SESL paketlerini GitHub Packages’dan indirebilmek için depoda `GH_PACKAGES_USER` ve `GH_PACKAGES_TOKEN` Actions secret’ları tanımlı olmalıdır; token’da `read:packages` erişimi gerekir. Token’ları kaynak koduna eklemeyin.

[Bu modlu APK’yı üreten başarılı Actions run’ı](https://github.com/k516crypro/OneOs-Data-Transfer-Modded/actions/runs/43904712220) derlemeyi ve artifact yüklemeyi doğrular. Bu, cihaz üzerinde çalışma testi yapıldığı anlamına gelmez.

## Test durumu ve güvenlik

Derleme CI’da başarılıdır; OxygenOS 16 Guest açılışı, otomatik dönüş, aktarım, iptal, düşük depolama ve Galeri’de görünürlük gerçek cihazda ayrıca test edilmelidir. UI Activator değişikliği için henüz `Home activated!` logu ve normal OxygenOS oturumu ile doğrulama yoktur. Bu nedenle sorun kesin olarak çözüldü denemez.

OneOS root erişimi ve açıkça desteklenen cihaz/imaj profilleri gerektirir. Önemli verileri yedekleyin ve kritik olmayan bir cihazda test edin. Lisans ve atıflar için [`LICENSE`](LICENSE), [`NOTICE`](NOTICE) ve [`CREDITS.md`](CREDITS.md) dosyalarına bakın. OneLineage kaynaklı bileşenlerin bu OneOS dağıtımında kullanımı için ayrı yazılı izin alınmıştır; bu izin başka projelerde genel yeniden kullanım hakkı vermez.
