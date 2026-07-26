# Third-Party Notices

LionLibrary is built with the following open-source libraries and services.
This file lists their licenses and required attributions. LionLibrary's own
source code is licensed separately — see [LICENSE](./LICENSE).

## Metadata & Data Sources

**TMDB (The Movie Database)**
This product uses the TMDB API but is not endorsed or certified by TMDB.
API terms: https://www.themoviedb.org/documentation/api/terms-of-use

## Media Playback

**Media3 / ExoPlayer** — Apache License 2.0
Copyright Google LLC.
https://github.com/androidx/media

**libVLC (VideoLAN)** — GNU Lesser General Public License v2.1 (LGPLv2.1)
Copyright VideoLAN and VLC authors.
https://code.videolan.org/videolan/vlc-android
libVLC is used as a compiled library dependency without modification to its
source. Per LGPLv2.1, this permits use in a non-GPL application provided the
library remains dynamically linked (standard for Android AAR dependencies)
and any modifications to libVLC itself are shared under the same license.
This application does not modify libVLC's source.
Note: "VLC" and the VLC logo are trademarks of VideoLAN. LionLibrary is an
independent application and is not affiliated with or endorsed by VideoLAN.

## Core Framework & Architecture

**Kotlin** — Apache License 2.0, JetBrains
**Jetpack Compose / AndroidX / Material 3** — Apache License 2.0, Google LLC
**Koin** (dependency injection) — Apache License 2.0
**Room** (database) — Apache License 2.0, Google LLC
**DataStore** (preferences) — Apache License 2.0, Google LLC

## Networking & Data

**Retrofit** — Apache License 2.0, Square, Inc.
**OkHttp** — Apache License 2.0, Square, Inc.
**Kotlinx Serialization** — Apache License 2.0, JetBrains

## Images

**Coil** (image loading) — Apache License 2.0

---

Full license texts for Apache License 2.0 and LGPLv2.1 are available at:
- Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0
- LGPLv2.1: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
