# v2rayMF

v2rayMF is a personal fork of the popular [v2rayNG](https://github.com/2dust/v2rayNG) Android client. This fork adds a few handy features and tweaks that make managing connections and custom routing rules more flexible.

---

## What's Different (Key Features)

*   **Custom HTTP Headers per Subscription**: Easily configure custom user-agents or write any custom headers for your subscription requests.
*   **Automatic System Info Transmission**: Optional support to automatically generate and attach system parameters (`x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`) during subscription updates, with support for custom system overrides.
*   **Ignore Subscription Routing Rules**: A toggle to completely ignore/strip DNS and routing rules pushed by the VPN provider, forcing all traffic via proxy by default and letting you manage everything strictly with your own local rules.
*   **Local File Ruleset Import & Export**: Directly import and export custom routing rulesets from/to `.json` files on your device. This allows loading huge files that exceed your device's clipboard capacity.

---

## How to Build

If you want to compile `v2rayMF` yourself:

1. Open the inner `V2rayNG` folder in **Android Studio**.
2. Download `libv2ray.aar` from [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite/releases) and place it into `V2rayNG/app/libs/`.
3. Press **Run** (or build a clean APK via *Build -> Build APKs*).

---

## Credits & Acknowledgement

*   **AI Assistance**: v2rayMF has been developed with the help of AI.
*   **v2rayNG**: Huge thanks to the developers of the original [v2rayNG](https://github.com/2dust/v2rayNG) client.
