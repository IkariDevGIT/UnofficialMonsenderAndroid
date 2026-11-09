<p align="center">
  <img src=".github/assets/banner.png" alt="Unofficial MonSender, with Kitty peeking over the edge">
</p>

An unofficial Android companion for [MonLoader](https://github.com/monbooru/monloader), inspired by [MonSender](https://github.com/monbooru/monsender). Copy a link in any app, swipe up on the prompt, and it lands in MonLoader's download queue, ready for [monbooru](https://github.com/monbooru/monbooru).

This project is not affiliated with monbooru and is not made or maintained by the monbooru maintainers.

<table align="center">
  <tr>
    <td align="center"><img src=".github/assets/send-prompt.png" width="180" alt=""><br><sub><b>Send prompt</b><br>Copy a link, swipe up</sub></td>
    <td align="center"><img src=".github/assets/detections.png" width="180" alt=""><br><sub><b>Detections</b><br>Live MonLoader job status</sub></td>
    <td align="center"><img src=".github/assets/sent.png" width="180" alt=""><br><sub><b>Sent</b><br>Queued in MonLoader</sub></td>
    <td align="center"><img src=".github/assets/settings.png" width="180" alt=""><br><sub><b>Settings</b><br>Server, pairing, filters</sub></td>
  </tr>
</table>

Meet **Kitty**, who keeps you company while you collect. She blinks, scratches, licks her paws and meows when you pet her. Pet her a lot and she might come a little closer (=^･ω･^=)

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

**Just looking around?** Enter `test` as the server address. The app then talks to a built-in pretend MonLoader, so you can pair, send and watch jobs finish without a server.

## Troubleshooting

**Copy detection keeps turning itself off.** Some phones (Samsung, Xiaomi, Huawei, Oppo and others) stop background apps to save battery, which also switches off their accessibility service. Set the app's battery usage to Unrestricted in App info, and on Samsung add it to Never sleeping apps. See [dontkillmyapp.com](https://dontkillmyapp.com) for your phone.

## Contributing

Pull requests are welcome as long as you've reviewed them at least a bit.

## License

[0BSD](LICENSE). Bundled assets keep their own licenses: JetBrains Mono and Dela Gothic One under the [SIL Open Font License](app/src/main/assets/licenses), and Kitty's meow from [BigSoundBank](https://bigsoundbank.com) under CC0.
