# Privacy and storage

LionLibrary has no account, analytics SDK, cloud library sync or app telemetry.
Videos and subtitles stay in the folders you select through Android's folder picker.
Scanning, automatic recognition retries and key verification send search titles and your own API key
to TMDB. Artwork is downloaded from TMDB's image service into private app storage.
Opening movie details may fetch a Letterboxd rating; opening its link launches your
browser. External playback grants temporary read access to the selected player.

Room stores titles, local URIs, metadata, watchlist and progress on this device.
Android backup includes that database and portable playback preferences. It excludes
TMDB credentials, folder preferences/grants and downloaded artwork. After restore,
reconnect your Movies and Shows folders, supply your key and run Scan Library to
recover missing artwork. Auto-scan also attempts recovery when connected.
The database contains file URIs as library identities; they are not access grants.
Files are never deleted by scanning, repair or an availability check.
Rescanning removes library entries and associated progress for files proven missing
from a successfully read selected folder. Unavailable or partially read folders retain
their entries. Unrelated files and library records stay untouched.

Delete from device is a separate, explicitly confirmed action. Home offers deletion
of a movie's video file or a safely identified show's entire folder; episode menus
offer deletion of that episode's video file. The confirmation names the exact path
and warns that deletion is permanent. Show-folder deletion includes its contents;
movie/episode deletion leaves neighboring files and sidecar subtitles in place.
Shared, ambiguous, changed or protected targets are refused. If one physical file
contains several episodes, deleting it removes that entire episode range.
Clear watch history removes progress only; uninstalling removes private app data.
Android/your backup provider controls retention of system backups.

Debug builds redact API keys in HTTP logs. Release builds disable HTTP logging.
This privacy statement must be shipped with and reviewed against the final APK.
