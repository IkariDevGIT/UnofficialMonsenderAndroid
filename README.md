# Unofficial MonSender

An unofficial Android companion for [MonLoader](https://github.com/monbooru/monloader). Copy a link in any app, swipe up on the prompt, and it lands in MonLoader's download queue.

Not affiliated with the monbooru project.

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

## License

[MIT](LICENSE)
