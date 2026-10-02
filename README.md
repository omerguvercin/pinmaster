# PinMaster — Otomatik Uygulama Sabitleme

PinMaster, Android'de seçtiğiniz uygulamaları otomatik olarak **App Pinning (Ekrana Sabitleme)** moduna alan bir güvenlik uygulamasıdır. Telefonu bir çocuğa, müşteriye veya başka birine verdiğinizde yalnızca izin verdiğiniz uygulamaların kullanılabilmesini sağlar.

---

## Özellikler

- 🔒 **Sessiz & Otomatik Sabitleme** — Arka planda çalışan bir shell daemon, hedef uygulamayı açar açmaz `am task lock` komutuyla anında kilitler; hiçbir ekran etkileşimi gerekmez.
- ⚙️ **Sabitleme Gecikmesi** — Slider ile 100 ms – 1000 ms arasında özelleştirilebilir gecikme.
- 📳 **Titreşim Geri Bildirimi** — Sabitleme gerçekleştiğinde isteğe bağlı titreşim.
- ⏸️ **Korumayı Duraklat / Başlat** — Tek tuşla tüm sabitleme korumasını geçici olarak devre dışı bırakın.
- 🎨 **Sade & Modern Arayüz** — Koyu tema, Türkçe dil desteği.

---

## Gereksinimler

| Gereksinim | Açıklama |
|---|---|
| Android | 8.0 (API 26) veya üzeri |
| Sistem Sabitleme | Ayarlar → Güvenlik → Ekrana Sabitleme → **Açık** olmalı |
| Shizuku / Root | Arka plan daemon'ı için `am task lock` yetkisi gerekir |

---

## Kurulum & Kullanım

1. APK'yı yükleyin.
2. **Ayarlar → Güvenlik → Ekrana Sabitleme** özelliğini etkinleştirin.
3. PinMaster'ı açın ve **Havuz** sekmesinden sabitlemek istediğiniz uygulamaları seçin.
4. Daemon'ı başlatmak için Shizuku veya ADB üzerinden:
   ```sh
   adb shell sh /storage/emulated/0/Android/data/com.pinmaster.app/files/pin_daemon.sh &
   ```
5. Seçtiğiniz uygulamayı açtığınızda otomatik olarak sabitlenecektir.

---

## Geliştirici

**Ömer Güvercin**
- E-posta: omerguvercinn@gmail.com
- WhatsApp: @omerguvercinn

---

## Lisans

Bu proje özel kullanım amaçlıdır. İzinsiz dağıtım ve ticari kullanım yasaktır.
