# Browse extensions in Hikari S

Hikari S is the **app**, not a mirror of extension APKs. Its Extensions screen reads catalogs from their maintainers and lets you search, inspect, install, update, and remove compatible sources. This page is the quick map; the app remains the place to browse live listings.

## Open the in-app browser

1. Open **Extensions** from the navigation bar.
2. Choose **Add repo** and select the repository format you were given.
3. Open the repository card, search its catalog, and install only the source you want.
4. Installed sources appear in the Extensions list; use each row's settings gear when that source exposes settings.

Some starter repositories are preloaded on a fresh install. Others can be added from their maintainers' repository URL. Hikari S does not re-host their catalogs or binaries.

## Catalogs and formats

| Ecosystem | What it contains | Browse the maintainer's project | Repository/index URL to add |
|---|---|---|---|
| Hikari `.hiki` | Native Hikari providers and compatible CloudStream bridges | [Hikari extensions source](https://github.com/codegeasse1/hikari-extensions) | [`repo.json`](https://raw.githubusercontent.com/codegeasse1/hikari-extensions/builds/repo.json) |
| CloudStream `.cs3` | CloudStream plugins adapted by Hikari's CS3 bridge | [Codegeasse CloudStream repository](https://github.com/codegeasse1/codegeasse-cloudstream-repos) | [`repo.json`](https://raw.githubusercontent.com/codegeasse1/codegeasse-cloudstream-repos/builds/repo.json) |
| Aniyomi / Mihon | Anime sources distributed as Android extension APKs | [Aniyomi extensions](https://github.com/aniyomiorg/aniyomi-extensions) | [`index.min.json`](https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json) |
| Manga / Manhwa | Manga sources distributed as Android extension APKs | [Keiyoushi extensions](https://github.com/keiyoushi/extensions) | [`index.json`](https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json) |
| SkyStream | SkyStream JavaScript providers | [SkyStream plugins](https://github.com/akashdh11/skystream-plugins) | [`repo.json`](https://raw.githubusercontent.com/akashdh11/skystream-plugins/main/repo.json) |
| Vega | Vega scraper providers | [Vega providers](https://github.com/Zenda-Cross/vega-providers) | [`manifest.json`](https://raw.githubusercontent.com/Zenda-Cross/vega-providers/main/manifest.json) |
| Zangetsu | Zangetsu source definitions | [Zangetsu providers](https://github.com/Spyou/zangetsu-providers) | [`index.json`](https://raw.githubusercontent.com/Spyou/zangetsu-providers/main/index.json) |
| LNReader | Novel-source plugins | [LNReader plugins](https://github.com/lnreader/lnreader-plugins) | Add the repository URL shown by the plugin maintainer |
| Sora / AnymeX | Source descriptors and scripts | [StreamEx example source](https://github.com/justbbcr/streamex) | Add a maintainer-provided JSON descriptor or array |
| Nuvio | Nuvio JavaScript providers | [Nuvio providers](https://github.com/tapframe/nuvio-providers) | [`manifest.json`](https://raw.githubusercontent.com/tapframe/nuvio-providers/main/manifest.json) |
| Kotatsu | Manga parsers bundled from Kotatsu's parser project | [Kotatsu parsers](https://github.com/KotatsuApp/kotatsu-parsers) | Browse and install through the app's Kotatsu repository card |
| Stremio | Individual addons, not a single central catalog | Browse any addon maintainer's project | Paste its public `manifest.json` URL in **Add Stremio addon** |
| Universal scraper | User-supplied JSON rules for HTML or JSON APIs | No central catalog is assumed | Paste a scraper config in **Add scraper** |

The in-app add-repository dialog displays a format-specific example URL and accepts supported short names where available. Catalogs change independently; check the maintainer's repository for current releases and compatibility notes.

## Where the code lives in this repository

- [Extensions browser and repository dialogs](../app/src/main/java/com/hikari/app/ui/screens/ExtensionsScreen.kt)
- [Aniyomi extension loader](../app/src/main/java/com/hikari/app/aniyomi/AniyomiExtensionManager.kt)
- [Manga extension loader](../app/src/main/java/com/hikari/app/manga/MangaExtensionManager.kt)
- [Hikari provider SDK and extension notes](HIKARI_EXTENSIONS.md)
- [Extension settings behavior](EXTENSION_SETTINGS.md)
- [Upstream Hikari sync guide](UPSTREAM_SYNC.md)

## Safety and provenance

Extensions may run third-party code or make network requests. Only add catalogs and install sources you trust. Hikari S links to upstream maintainers rather than claiming their projects, re-signing their packages, or mirroring their binaries. Review an extension's own license and release notes before using or redistributing it.
