package com.hikari.app.data

import com.hikari.app.HikariApp
import com.hikari.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object TrackerLibraryRepository {
    data class Shelf(val key: String, val title: String, val items: List<MediaItem>)

    /** Why a tracker pick last produced nothing, keyed by pick key
     *  (`tracker:trakt`) — read by Home's empty state so a dead login says
     *  so instead of a blank "Couldn't load". */
    val errors = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun load(app: HikariApp, sourceOverride: String? = null): Result<List<Shelf>> = withContext(Dispatchers.IO) {
        val key = (sourceOverride ?: runCatching { app.store.trackerLibrarySource() }.getOrDefault("nuvio")).lowercase()
        val r = when (key) {
            "trakt" -> loadTrakt(app)
            "simkl" -> loadSimkl(app)
            "anilist" -> loadAniList(app)
            "mal" -> loadMal(app)
            "kitsu" -> loadKitsu(app)
            "shikimori" -> loadShikimori(app)
            "mdblist" -> Result.failure(IllegalStateException("MDBList library requires a connected MDBList account."))
            else -> Result.success(emptyList())
        }
        r.onFailure { errors["tracker:$key"] = it.message ?: "couldn't load" }
            .onSuccess { errors.remove("tracker:$key") }
        r
    }

    private suspend fun loadTrakt(app: HikariApp): Result<List<Shelf>> {
        val account = app.store.trackers().firstOrNull { it.kind == TrackerKind.TRAKT }
            ?: return Result.failure(IllegalStateException("Connect Trakt in Settings → Trackers first."))
        val client = app.store.trackerClients().firstOrNull { it.kind == TrackerKind.TRAKT }
            ?: TrackerClient(TrackerKind.TRAKT)
        if (!client.ready) return Result.failure(IllegalStateException("Trakt app credentials are not configured."))
        val headers = mapOf("Authorization" to "Bearer ${account.token}", "trakt-api-version" to "2", "trakt-api-key" to client.id)
        var user = account.user
        if (user.isBlank()) {
            user = runCatching {
                val r = Http.get("https://api.trakt.tv/users/settings", headers)
                if (r.code == 401) return Result.failure(IllegalStateException("Trakt login expired (HTTP 401) — reconnect Trakt in Settings → Trackers."))
                if (!r.isSuccessful) return Result.failure(IllegalStateException("Trakt username is unavailable; reconnect Trakt."))
                org.json.JSONObject(r.body?.string().orEmpty()).optJSONObject("user")?.optString("username").orEmpty()
            }.getOrDefault("")
        }
        if (user.isBlank()) return Result.failure(IllegalStateException("Trakt username is unavailable; reconnect Trakt."))
        val out = ArrayList<Shelf>()

        var traktUnauthorized = false
        suspend fun get(path: String): JSONArray? = runCatching {
            val response = Http.get("https://api.trakt.tv$path", headers)
            if (response.code == 401) { traktUnauthorized = true; return@runCatching null }
            if (!response.isSuccessful) {
                Logs.log("Tracker", "trakt $path HTTP " + response.code)
                return@runCatching null
            }
            JSONArray(response.body?.string().orEmpty())
        }.getOrNull()

        val movieArray = get("/users/${enc(user)}/watchlist/movies?extended=full&page=1&limit=250") ?: JSONArray()
        val movies = buildList {
            for (i in 0 until movieArray.length()) {
                traktItem(movieArray.optJSONObject(i), MediaType.MOVIE)?.let(::add)
            }
        }
        val showArray = get("/users/${enc(user)}/watchlist/shows?extended=full&page=1&limit=250") ?: JSONArray()
        val shows = buildList {
            for (i in 0 until showArray.length()) {
                traktItem(showArray.optJSONObject(i), MediaType.SERIES)?.let(::add)
            }
        }
        if (movies.isNotEmpty()) out += Shelf("trakt.watchlist.movies", "Trakt Watchlist · Movies", movies)
        if (shows.isNotEmpty()) out += Shelf("trakt.watchlist.shows", "Trakt Watchlist · Shows", shows)
        val histMovies = get("/users/${enc(user)}/watched/movies?extended=full&page=1&limit=250") ?: JSONArray()
        val hm = buildList {
            for (i in 0 until histMovies.length()) {
                traktItem(histMovies.optJSONObject(i), MediaType.MOVIE)?.let(::add)
            }
        }.distinctBy { it.uniqueId }
        if (hm.isNotEmpty()) out += Shelf("trakt.history.movies", "Trakt History · Movies", hm)
        val histShows = get("/users/${enc(user)}/watched/shows?extended=full&page=1&limit=250") ?: JSONArray()
        val hs = buildList {
            for (i in 0 until histShows.length()) {
                traktItem(histShows.optJSONObject(i), MediaType.SERIES)?.let(::add)
            }
        }.distinctBy { it.uniqueId }
        if (hs.isNotEmpty()) out += Shelf("trakt.history.shows", "Trakt History · Shows", hs)
        val collMovies = get("/users/${enc(user)}/collection/movies?extended=full&page=1&limit=250") ?: JSONArray()
        val cm = buildList {
            for (i in 0 until collMovies.length()) {
                traktItem(collMovies.optJSONObject(i), MediaType.MOVIE)?.let(::add)
            }
        }.distinctBy { it.uniqueId }
        if (cm.isNotEmpty()) out += Shelf("trakt.collection.movies", "Trakt Collection · Movies", cm)
        val collShows = get("/users/${enc(user)}/collection/shows?extended=full&page=1&limit=250") ?: JSONArray()
        val cs = buildList {
            for (i in 0 until collShows.length()) {
                traktItem(collShows.optJSONObject(i), MediaType.SERIES)?.let(::add)
            }
        }.distinctBy { it.uniqueId }
        if (cs.isNotEmpty()) out += Shelf("trakt.collection.shows", "Trakt Collection · Shows", cs)

        val lists = runCatching {
            val response = Http.get("https://api.trakt.tv/users/${enc(user)}/lists", headers)
            if (!response.isSuccessful) JSONArray() else JSONArray(response.body?.string().orEmpty())
        }.getOrDefault(JSONArray())

        for (i in 0 until lists.length()) {
            val list = lists.optJSONObject(i) ?: continue
            val ids = list.optJSONObject("ids")
            val slug = ids?.optString("slug").orEmpty().ifBlank {
                list.optString("name").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            }
            if (slug.isBlank()) continue
            val response = runCatching {
                Http.get("https://api.trakt.tv/users/${enc(user)}/lists/${enc(slug)}/items?extended=full&page=1&limit=1000", headers)
            }.getOrNull() ?: continue
            if (!response.isSuccessful) continue
            val array = runCatching { JSONArray(response.body?.string().orEmpty()) }.getOrNull() ?: continue
            val items = buildList {
                for (j in 0 until array.length()) {
                    val row = array.optJSONObject(j) ?: continue
                    val type = row.optString("type")
                    val media = row.optJSONObject(type) ?: continue
                    val mt = if (type == "movie") MediaType.MOVIE else MediaType.SERIES
                    traktMedia(media, mt)?.let(::add)
                }
            }.distinctBy { it.uniqueId }
            if (items.isNotEmpty()) out += Shelf("trakt.list.$slug", list.optString("name", slug), items)
        }
        if (out.isEmpty() && traktUnauthorized) return Result.failure(IllegalStateException("Trakt login expired (HTTP 401) — reconnect Trakt in Settings → Trackers."))
        return Result.success(out)
    }

    private suspend fun loadSimkl(app: HikariApp): Result<List<Shelf>> {
        val account = app.store.trackers().firstOrNull { it.kind == TrackerKind.SIMKL }
            ?: return Result.failure(IllegalStateException("Connect Simkl in Settings → Trackers first."))
        val client = app.store.trackerClients().firstOrNull { it.kind == TrackerKind.SIMKL }
            ?: TrackerClient(TrackerKind.SIMKL)
        if (!client.ready) return Result.failure(IllegalStateException("Simkl app credentials are not configured."))
        val response = runCatching {
            Http.get("https://api.simkl.com/sync/all-items/?extended=full", mapOf(
                "Authorization" to "Bearer ${account.token}",
                "simkl-api-key" to client.id,
            ))
        }.getOrElse { return Result.failure(it) }
        if (!response.isSuccessful) return Result.failure(IllegalStateException("Simkl returned HTTP ${response.code}."))
        val root = JSONObject(response.body?.string().orEmpty())
        val out = ArrayList<Shelf>()

        fun parse(key: String, title: String, type: MediaType, anime: Boolean = false, manga: Boolean = false) {
            val array = root.optJSONArray(key) ?: return
            val items = buildList {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    val item = row.optJSONObject("movie") ?: row.optJSONObject("show") ?: row.optJSONObject("anime") ?: row.optJSONObject("manga") ?: row
                    simklMedia(item, type, anime, manga)?.let(::add)
                }
            }.distinctBy { it.uniqueId }
            if (items.isNotEmpty()) out += Shelf("simkl.$key", title, items)
        }
        parse("movies", "Simkl · Movies", MediaType.MOVIE)
        parse("tv_shows", "Simkl · TV Shows", MediaType.SERIES)
        parse("anime", "Simkl · Anime", MediaType.SERIES, anime = true)
        parse("manga", "Simkl · Manga", MediaType.SERIES, manga = true)
        return Result.success(out)
    }


    private suspend fun loadAniList(app: HikariApp): Result<List<Shelf>> {
        val account=app.store.trackers().firstOrNull{it.kind==TrackerKind.ANILIST}
            ?: return Result.failure(IllegalStateException("Connect AniList in Settings \u2192 Trackers first."))
        val out=ArrayList<Shelf>()
        // Anime lists, then manga lists: a tracker that also holds manga shows
        // both — manga rows carry rawType "manga", which is what sends their
        // tap to the manga engines instead of the video detail page.
        val animeLists=aniListCollection(app, account, "ANIME")
            ?: return Result.failure(IllegalStateException("AniList did not return a library."))
        for(i in 0 until animeLists.length()){
            val l=animeLists.optJSONObject(i)?:continue;val name=l.optString("name").ifBlank{"AniList"};val a=l.optJSONArray("entries")?:JSONArray()
            val items=buildList{for(j in 0 until a.length())aniListMedia(a.optJSONObject(j)?.optJSONObject("media"))?.let(::add)}.distinctBy{it.uniqueId}
            if(items.isNotEmpty())out+=Shelf("anilist."+i+"."+name,name,items)
        }
        val mangaLists=aniListCollection(app, account, "MANGA") ?: JSONArray()
        for(i in 0 until mangaLists.length()){
            val l=mangaLists.optJSONObject(i)?:continue;val name=l.optString("name").ifBlank{"AniList Manga"};val a=l.optJSONArray("entries")?:JSONArray()
            val items=buildList{for(j in 0 until a.length())aniListMedia(a.optJSONObject(j)?.optJSONObject("media"), manga=true)?.let(::add)}.distinctBy{it.uniqueId}
            if(items.isNotEmpty())out+=Shelf("anilist.manga."+i+"."+name,name,items)
        }
        return Result.success(out)
    }
    private suspend fun aniListCollection(app: HikariApp, account: com.hikari.app.data.TrackerAccount, type: String): JSONArray? {
        val query = "query(\$name:String){MediaListCollection(userName: \$name,type:"+type+"){lists{name entries{media{id title{userPreferred english romaji} coverImage{large} startDate{year} averageScore nextAiringEpisode{airingAt episode}}}}}}"
        // AniList's MediaListCollection is user-specific. The access token
        // must be sent as a Bearer token; without it a connected account can still
        // return an empty/unauthorized collection.
        val raw = Http.postStringQuiet(
            "https://graphql.anilist.co",
            JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put("name", anilistUser(app, account)))
                .toString(),
            mapOf(
                "Authorization" to "Bearer " + account.token,
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
        ) ?: return null
        return runCatching {
            JSONObject(raw).optJSONObject("data")?.optJSONObject("MediaListCollection")?.optJSONArray("lists")?:JSONArray()
        }.getOrDefault(JSONArray())
    }
    private suspend fun anilistUser(app: HikariApp, account: com.hikari.app.data.TrackerAccount): String {
        if (account.user.isNotBlank()) return account.user
        val raw = Http.postStringQuiet(
            "https://graphql.anilist.co",
            JSONObject().put("query", "{Viewer{name}}").toString(),
            mapOf("Authorization" to "Bearer " + account.token, "Accept" to "application/json", "Content-Type" to "application/json"),
        ) ?: return ""
        return JSONObject(raw).optJSONObject("data")?.optJSONObject("Viewer")?.optString("name").orEmpty()
    }
    private suspend fun loadMal(app:HikariApp):Result<List<Shelf>>{
        val account=app.store.trackers().firstOrNull{it.kind==TrackerKind.MAL}
            ?:return Result.failure(IllegalStateException("Connect MyAnimeList in Settings → Trackers first."))
        // One authenticated probe so an expired token reports 401 instead of
        // five silent empty shelves.
        runCatching { Http.get("https://api.myanimelist.net/v2/users/@me", mapOf("Authorization" to "Bearer "+account.token)) }.getOrNull()?.let {
            if (it.code == 401) return Result.failure(IllegalStateException("MyAnimeList login expired (HTTP 401) — reconnect MAL in Settings → Trackers."))
        }
        val statuses=listOf("watching" to "Watching","plan_to_watch" to "Plan to Watch","completed" to "Completed","on_hold" to "On Hold","dropped" to "Dropped")
        val out=ArrayList<Shelf>()
        for((status,label) in statuses){
            val raw=Http.getStringQuiet("https://api.myanimelist.net/v2/users/@me/animelist?status="+status+"&limit=1000&fields=id,title,main_picture,start_date,mean",mapOf("Authorization" to "Bearer "+account.token))?:continue
            val a=JSONObject(raw).optJSONArray("data")?:continue
            val items=buildList{for(i in 0 until a.length())malMedia(a.optJSONObject(i)?.optJSONObject("node"))?.let(::add)}
            if(items.isNotEmpty())out+=Shelf("mal."+status,label,items)
        }
        // Manga lists live on a separate endpoint with their own statuses.
        val mangaStatuses=listOf("reading" to "Reading","plan_to_read" to "Plan to Read","completed" to "Completed","on_hold" to "On Hold","dropped" to "Dropped")
        for((status,label) in mangaStatuses){
            val raw=Http.getStringQuiet("https://api.myanimelist.net/v2/users/@me/mangalist?status="+status+"&limit=1000&fields=id,title,main_picture,start_date,mean",mapOf("Authorization" to "Bearer "+account.token))?:continue
            val a=JSONObject(raw).optJSONArray("data")?:continue
            val items=buildList{for(i in 0 until a.length())malMedia(a.optJSONObject(i)?.optJSONObject("node"), manga=true)?.let(::add)}
            if(items.isNotEmpty())out+=Shelf("mal.manga."+status,"Manga · "+label,items)
        }
        return Result.success(out)
    }
    private suspend fun loadKitsu(app:HikariApp):Result<List<Shelf>>{
        val account=app.store.trackers().firstOrNull{it.kind==TrackerKind.KITSU}
            ?:return Result.failure(IllegalStateException("Connect Kitsu in Settings → Trackers first."))
        if(account.userId.isBlank())return Result.failure(IllegalStateException("Kitsu user id is unavailable; reconnect Kitsu."))
        runCatching { Http.get("https://kitsu.io/api/edge/users/${enc(account.userId)}", mapOf("Authorization" to "Bearer "+account.token)) }.getOrNull()?.let {
            if (it.code == 401) return Result.failure(IllegalStateException("Kitsu login expired (HTTP 401) — reconnect Kitsu in Settings → Trackers."))
        }
        val raw=Http.getStringQuiet("https://kitsu.io/api/edge/users/"+enc(account.userId)+"/library-entries?page%5Blimit%5D=500&include=anime",mapOf("Authorization" to "Bearer "+account.token))
            ?:return Result.failure(IllegalStateException("Kitsu library could not be loaded — check the connection, then reconnect Kitsu if it stays empty."))
        val root=JSONObject(raw);val data=root.optJSONArray("data")?:JSONArray();val inc=root.optJSONArray("included")?:JSONArray();val anime=HashMap<String,JSONObject>()
        for(i in 0 until inc.length()){val o=inc.optJSONObject(i)?:continue;if(o.optString("type")=="anime")anime[o.optString("id")]=o}
        val groups=linkedMapOf<String,MutableList<MediaItem>>()
        for(i in 0 until data.length()){
            val e=data.optJSONObject(i)?:continue;val at=e.optJSONObject("attributes")?:continue
            val rel=e.optJSONObject("relationships")?.optJSONObject("anime")?.optJSONObject("data");val o=rel?.optString("id")?.let{anime[it]}?:continue;val aa=o.optJSONObject("attributes")?:continue
            val score=at.optDouble("ratingTwenty",0.0).takeIf{it>0}?.div(2.0)
            val item=MediaItem("kitsu",o.optString("id"),aa.optString("canonicalTitle").ifBlank{"Untitled"},MediaType.SERIES,aa.optJSONObject("posterImage")?.optString("original"),rawType="anime",rating=score,metadataSource="Kitsu")
            groups.getOrPut(at.optString("status").ifBlank{"unknown"}){ArrayList()}.add(item)
        }
        val shelves=ArrayList<Shelf>(groups.map { entry -> val status = entry.key; Shelf("kitsu."+status, "Kitsu · "+status.replace('_',' ').replaceFirstChar{it.uppercase()}, entry.value) })
        // Manga entries live beside the anime ones, keyed the same way but
        // including manga instead of anime.
        runCatching {
            val mraw=Http.getStringQuiet("https://kitsu.io/api/edge/users/"+enc(account.userId)+"/library-entries?page%5Blimit%5D=500&include=manga",mapOf("Authorization" to "Bearer "+account.token))
            if(!mraw.isNullOrBlank()){
                val mroot=JSONObject(mraw);val mdata=mroot.optJSONArray("data")?:JSONArray();val minc=mroot.optJSONArray("included")?:JSONArray();val manga=HashMap<String,JSONObject>()
                for(i in 0 until minc.length()){val o=minc.optJSONObject(i)?:continue;if(o.optString("type")=="manga")manga[o.optString("id")]=o}
                val mgroups=linkedMapOf<String,MutableList<MediaItem>>()
                for(i in 0 until mdata.length()){
                    val e=mdata.optJSONObject(i)?:continue;val at=e.optJSONObject("attributes")?:continue
                    val rel=e.optJSONObject("relationships")?.optJSONObject("manga")?.optJSONObject("data");val o=rel?.optString("id")?.let{manga[it]}?:continue;val aa=o.optJSONObject("attributes")?:continue
                    val score=at.optDouble("ratingTwenty",0.0).takeIf{it>0}?.div(2.0)
                    val item=MediaItem("kitsu",o.optString("id"),aa.optString("canonicalTitle").ifBlank{"Untitled"},MediaType.SERIES,aa.optJSONObject("posterImage")?.optString("original"),rawType="manga",rating=score,metadataSource="Kitsu")
                    mgroups.getOrPut(at.optString("status").ifBlank{"unknown"}){ArrayList()}.add(item)
                }
                mgroups.forEach { entry -> val status = entry.key; shelves += Shelf("kitsu.manga."+status, "Kitsu · Manga · "+status.replace('_',' ').replaceFirstChar{it.uppercase()}, entry.value) }
            }
        }
        return Result.success(shelves)
    }
    private suspend fun loadShikimori(app:HikariApp):Result<List<Shelf>>{
        val account=app.store.trackers().firstOrNull{it.kind==TrackerKind.SHIKIMORI}
            ?:return Result.failure(IllegalStateException("Connect Shikimori in Settings → Trackers first."))
        runCatching { Http.get("https://shikimori.one/api/users/whoami", mapOf("Authorization" to "Bearer "+account.token)) }.getOrNull()?.let {
            if (it.code == 401) return Result.failure(IllegalStateException("Shikimori login expired (HTTP 401) — reconnect Shikimori in Settings → Trackers."))
        }
        val uid=account.userId.ifBlank{
            val raw=Http.getStringQuiet("https://shikimori.one/api/users/whoami",mapOf("Authorization" to "Bearer "+account.token))
            JSONObject(raw?:return Result.failure(IllegalStateException("Shikimori account id is unavailable — reconnect Shikimori."))).optInt("id",0).toString()
        }
        suspend fun pages(target: String, status: String, key: String): List<org.json.JSONObject> {
            val rows = ArrayList<org.json.JSONObject>()
            for (page in 1..20) {
                val raw=Http.getStringQuiet("https://shikimori.one/api/user_rates?user_id="+uid+"&target_type="+target+"&status="+status+"&limit=100&page="+page,mapOf("Authorization" to "Bearer "+account.token))?:break
                val a=runCatching { JSONArray(raw) }.getOrNull() ?: break
                if (a.length() == 0) break
                for(i in 0 until a.length()){ a.optJSONObject(i)?.let { rows += it } }
                if (a.length() < 100) break
            }
            return rows
        }
        val statuses=listOf("watching" to "Watching","planned" to "Planned","completed" to "Completed","on_hold" to "On Hold","dropped" to "Dropped","rewatching" to "Rewatching")
        val out=ArrayList<Shelf>()
        for((status,label) in statuses){
            val rows = pages("Anime", status, "anime")
            val items=buildList{for(r in rows)shikiMedia(r.optJSONObject("anime"))?.let(::add)}.distinctBy { it.uniqueId }
            if(items.isNotEmpty())out+=Shelf("shikimori."+status,"Shikimori · "+label,items)
        }
        for((status,label) in statuses){
            val rows = pages("Manga", status, "manga")
            val items=buildList{for(r in rows)shikiMedia(r.optJSONObject("manga"), manga=true)?.let(::add)}.distinctBy { it.uniqueId }
            if(items.isNotEmpty())out+=Shelf("shikimori.manga."+status,"Shikimori · Manga · "+label,items)
        }
        return Result.success(out)
    }
    private fun aniListMedia(o:JSONObject?, manga: Boolean = false):MediaItem?{
        if(o==null)return null;val id=o.optInt("id",0);if(id<=0)return null;val t=o.optJSONObject("title")
        val title=t?.optString("userPreferred").orEmpty().ifBlank{t?.optString("english").orEmpty()}.ifBlank{t?.optString("romaji").orEmpty()}.ifBlank{"Untitled"}
        // English title as the search key; display stays the tracker's own
        // (see AnimeCatalogRepository — romaji display titles match no
        // database, which is what left tracker episodes bare).
        val english=t?.optString("english")?.trim().orEmpty().takeIf{it.isNotBlank()&&!it.equals(title,ignoreCase=true)}.orEmpty()
        val next=o.optJSONObject("nextAiringEpisode")?.optLong("airingAt",0L)?.takeIf{it>0}?.let{java.time.Instant.ofEpochSecond(it).toString()}
        return MediaItem("anilist",id.toString(),title,MediaType.SERIES,o.optJSONObject("coverImage")?.optString("large"),o.optJSONObject("startDate")?.optInt("year",0)?.takeIf{it>0},rawType=if(manga)"manga" else "anime",rating=o.optDouble("averageScore",0.0).takeIf{it>0}?.div(10.0),nextEpisodeDate=next,metadataSource="AniList",originalTitle=english)
    }
    private fun malMedia(o:JSONObject?, manga: Boolean = false):MediaItem?{
        if(o==null)return null;val id=o.optInt("id",0);if(id<=0)return null
        return MediaItem("mal",id.toString(),o.optString("title").ifBlank{o.optJSONObject("title")?.optString("title").orEmpty()}.ifBlank{"Untitled"},MediaType.SERIES,o.optJSONObject("main_picture")?.optString("large")?.ifBlank{o.optJSONObject("main_picture")?.optString("medium")},o.optString("start_date").take(4).toIntOrNull(),rawType=if(manga)"manga" else "anime",rating=o.optDouble("mean",0.0).takeIf{it>0},metadataSource="MyAnimeList")
    }
    private fun shikiMedia(o:JSONObject?, manga: Boolean = false):MediaItem?{
        if(o==null)return null;val id=o.optInt("id",0);if(id<=0)return null
        val rawImg = o.optString("image").trim().ifBlank{o.optJSONObject("image")?.optString("original").orEmpty()}
        val img = when {
            rawImg.startsWith("http") -> rawImg
            rawImg.startsWith("//") -> "https:$rawImg"
            rawImg.startsWith("/") -> "https://shikimori.one$rawImg"
            rawImg.isNotBlank() -> "https://shikimori.one/$rawImg"
            else -> null
        }
        return MediaItem("shikimori",id.toString(),o.optString("name").ifBlank{o.optString("russian").ifBlank{"Untitled"}},MediaType.SERIES,img,o.optString("aired_on").take(4).toIntOrNull(),rawType=if(manga)"manga" else "anime",metadataSource="Shikimori")
    }
    private fun traktItem(row: JSONObject?, type: MediaType): MediaItem? =
        row?.let { traktMedia(it.optJSONObject("movie") ?: it.optJSONObject("show") ?: return@let null, type) }

    private fun traktMedia(o: JSONObject, type: MediaType): MediaItem? {
        val ids = o.optJSONObject("ids") ?: return null
        val tmdb = ids.optInt("tmdb", 0)
        val imdb = ids.optString("imdb").takeIf { it.startsWith("tt") }
        val id = if (tmdb > 0) tmdb.toString() else imdb ?: return null
        val provider = if (tmdb > 0) "tmdb" else "stremio"
        val image = o.optJSONObject("images")?.optJSONObject("poster")?.optString("full")
        return MediaItem(providerId = provider, id = id, title = o.optString("title").ifBlank { "Untitled" },
            type = type, posterUrl = image, year = o.optInt("year", 0).takeIf { it > 0 },
            rawType = if (provider == "tmdb") "tmdb" else if (type == MediaType.MOVIE) "movie" else "series")
    }

    private fun simklMedia(o: JSONObject, type: MediaType, anime: Boolean = false, manga: Boolean = false): MediaItem? {
        val ids = o.optJSONObject("ids") ?: return null
        val tmdb = ids.optInt("tmdb", 0)
        val imdb = ids.optString("imdb").takeIf { it.startsWith("tt") }
        // Anime Simkl has not mapped yet carries neither id: keep it as a
        // tracker row (playable by title search) instead of dropping it from
        // the shelf entirely. Only anime gets this fallback — anything else
        // would misread a live-action row as anime downstream.
        // Manga rows get the same treatment (a Simkl manga id instead): a
        // manga entry must never fall through to the anime path.
        val simklId = ids.optInt("simkl", 0)
        var id: String? = if (tmdb > 0) tmdb.toString() else imdb
        if (id == null && (anime || manga)) id = simklId.takeIf { it > 0 }?.toString()
        val finalId = id ?: return null
        val provider = if (tmdb > 0) "tmdb" else if (imdb != null) "stremio" else "simkl"
        return MediaItem(providerId = provider, id = finalId,
            title = o.optString("title").ifBlank { o.optString("name") }.ifBlank { "Untitled" },
            type = type, year = o.optInt("year", 0).takeIf { it > 0 },
            posterUrl = simklPoster(o.optString("poster")),
            rawType = if (manga) "manga" else if (provider == "tmdb") "tmdb" else if (provider == "simkl") "anime" else if (type == MediaType.MOVIE) "movie" else "series")
    }

    /** Simkl serves bare image ids, not URLs (same shape as the detail lookup
     *  in TrackerAnimeResolver): values that already are URLs pass through. */
    private fun simklPoster(raw: String): String? {
        val p = raw.trim()
        if (p.isBlank() || p == "null") return null
        if (p.startsWith("http")) return p
        return "https://wsrv.nl/?url=https://simkl.in/posters/" + p + "_m.webp&q=90"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private fun JSONArray.orEmpty(): List<Any?> = List(length()) { opt(it) }
