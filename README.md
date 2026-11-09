# Unofficial MonSender

An unofficial Android companion for [MonLoader](https://github.com/monbooru/monloader), inspired by [MonSender](https://github.com/monbooru/monsender). Copy a link in any app, swipe up on the prompt, and it lands in MonLoader's download queue, ready for [monbooru](https://github.com/monbooru/monbooru).

This project is not affiliated with monbooru and is not made or maintained by the monbooru maintainers.

> [!WARNING]
> This is a prototype. It was vibecoded and has not been code reviewed, so neither users nor developers reusing the code should expect a flawless app. It mainly exists to demonstrate my idea for making downloading to monbooru easier on Android.
>
> Support is minimal and best effort only.

## Install

1. Download the latest APK from [Releases](../../releases).
2. Open it on your phone and allow installing from your browser or file manager when asked.

Requires Android 8.0 or newer.

## Setup

1. **Server:** enter your MonLoader address, e.g. `http://192.168.1.20:8456`.
2. **Pair:** tap Pair, then approve the request in MonLoader under Settings → Pairing.
3. **Copy detection:** under Link detection, tap Turn on and enable the app's accessibility service.

   On Android 13 and newer, Android may say the setting is restricted. Tap App info, then ⋮ → Allow restricted settings, and turn the service on again.

The quick settings tile and the share menu work without copy detection.

## Troubleshooting

**Copy detection keeps turning itself off.** Some phones (Samsung, Xiaomi, Huawei, Oppo and others) stop background apps to save battery, which also switches off their accessibility service. Set the app's battery usage to Unrestricted in App info, and on Samsung add it to Never sleeping apps. See [dontkillmyapp.com](https://dontkillmyapp.com) for your phone.

## Contributing

Pull requests are welcome as long as you've reviewed them at least a bit.

## License

[0BSD](LICENSE)
