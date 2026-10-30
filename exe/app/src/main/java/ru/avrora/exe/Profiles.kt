package ru.avrora.exe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------- профили, лента, лайки, статусы ----------
// Лента видна только вам двоим, она живёт в приложении. Аврора пишет в неё маркерами
// [пост: текст], [пост: текст | фото:id], [статус: текст], [лайк: слова из поста], [коммент: слова из поста | текст],
// [аватар: id фото]. Геспера видит ленту снимком (pageJson), её лайки и комментарии приходят с сервера (PageSync).

class ProfileStore(ctx: Context) {
    private val dir = ctx.filesDir
    private fun file(name: String) = File(dir, name)

    private fun write(name: String, text: String) {
        val f = file(name)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }

    // профили
    private fun readProfiles(): JSONObject =
        runCatching { JSONObject(file("exe_profiles.json").readText()) }.getOrElse { JSONObject() }

    @Synchronized
    fun profile(owner: String): Profile {
        val o = readProfiles().optJSONObject(owner) ?: return Profile()
        return Profile(o.optString("avatar"), o.optString("status"), o.optLong("statusAt"))
    }

    private fun saveProfile(owner: String, p: Profile) {
        val all = readProfiles()
        all.put(owner, JSONObject().put("avatar", p.avatar).put("status", p.status).put("statusAt", p.statusAt))
        write("exe_profiles.json", all.toString())
    }

    @Synchronized
    fun setStatus(owner: String, text: String, now: Long) =
        saveProfile(owner, profile(owner).copy(status = text.trim().take(STATUS_MAX), statusAt = now))

    @Synchronized
    fun setAvatar(owner: String, avatar: String) = saveProfile(owner, profile(owner).copy(avatar = avatar))

    // посты
    @Synchronized
    fun posts(): List<PostItem> {
        val a = runCatching { JSONArray(file("exe_posts.json").readText()) }.getOrElse { JSONArray() }
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ca = o.optJSONArray("comments") ?: JSONArray()
            PostItem(
                o.optString("id"), o.optString("author"), o.optString("text"), o.optString("photo"),
                o.optLong("at"), o.optBoolean("likedByMe"), o.optBoolean("likedByHer"), o.optBoolean("likedByHesp"),
                (0 until ca.length()).map { ca.getJSONObject(it) }.map { PostComment(it.optString("who"), it.optString("text"), it.optLong("at")) },
                o.optBoolean("likedByEst")
            )
        }
    }

    private fun savePosts(list: List<PostItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("author", it.author).put("text", it.text).put("photo", it.photo)
                    .put("at", it.at).put("likedByMe", it.likedByMe).put("likedByHer", it.likedByHer).put("likedByHesp", it.likedByHesp).put("likedByEst", it.likedByEst)
                    .put("comments", JSONArray().also { a -> it.comments.forEach { c -> a.put(JSONObject().put("who", c.who).put("text", c.text).put("at", c.at)) } })
            )
        }
        write("exe_posts.json", a.toString())
    }

    @Synchronized
    fun addPost(p: PostItem) = savePosts((posts() + p).takeLast(200))

    @Synchronized
    fun removePost(id: String) = savePosts(posts().filter { it.id != id })

    /** who: AUTHOR_ME, AUTHOR_HER или WHO_HESPERA. */
    @Synchronized
    fun setLike(id: String, who: String, on: Boolean) = savePosts(posts().map {
        if (it.id != id) it else when (who) {
            AUTHOR_ME -> it.copy(likedByMe = on)
            WHO_HESPERA -> it.copy(likedByHesp = on)
            WHO_EST -> it.copy(likedByEst = on)
            else -> it.copy(likedByHer = on)
        }
    })

    /** Комментарий под постом. Тот же текст от того же автора дважды не добавляется. Возвращает пост или null. */
    @Synchronized
    fun addComment(id: String, who: String, text: String, now: Long = System.currentTimeMillis()): PostItem? {
        val t = text.replace(Regex("\\s+"), " ").trim().take(COMMENT_MAX)
        if (t.isEmpty()) return null
        var hit: PostItem? = null
        savePosts(posts().map {
            if (it.id != id) it
            else if (it.comments.any { c -> c.who == who && c.text == t }) it.also { p -> hit = p }
            else it.copy(comments = (it.comments + PostComment(who, t, now)).takeLast(60)).also { p -> hit = p }
        })
        return hit
    }

    @Synchronized
    fun removeComment(id: String, who: String, at: Long) = savePosts(posts().map {
        if (it.id != id) it else it.copy(comments = it.comments.filterNot { c -> c.who == who && c.at == at })
    })

    @Synchronized
    fun countSince(author: String, since: Long): Int = posts().count { it.author == author && it.at >= since }

    /**
     * Снимок ленты для сервера: так Геспера видит страницы Адониса и Авроры.
     * withPhoto — приложить фото из самого свежего поста со снимком (сервер решит, показать ли его ей).
     */
    fun pageJson(withPhoto: Boolean = false): JSONObject {
        val a = JSONArray()
        posts().takeLast(12).forEach { p ->
            val liked = JSONArray()
            if (p.likedByMe) liked.put(AUTHOR_ME)
            if (p.likedByHer) liked.put(AUTHOR_HER)
            if (p.likedByHesp) liked.put(WHO_HESPERA)
            if (p.likedByEst) liked.put(WHO_EST)
            val cs = JSONArray()
            p.comments.takeLast(6).forEach { c -> cs.put(JSONObject().put("who", c.who).put("text", c.text.take(300))) }
            a.put(JSONObject().put("id", p.id).put("author", p.author).put("text", p.text.take(600)).put("photo", p.photo.isNotBlank())
                .put("at", p.at).put("liked_by", liked).put("comments", cs))
        }
        val out = JSONObject().put("adonis_status", profile(AUTHOR_ME).status).put("aurora_status", profile(AUTHOR_HER).status).put("posts", a)
        if (withPhoto) {
            val p = posts().takeLast(3).lastOrNull { it.photo.startsWith("снимок:") }
            val b64 = p?.let { runCatching { encodeForModel(File(File(dir, "sent"), it.photo.removePrefix("снимок:"))) }.getOrNull() }
            if (p != null && b64 != null) out.put("photo_b64", b64).put("photo_post", p.id)
        }
        return out
    }

    /** Текст для неё: что сейчас на страницах и в ленте. */
    fun context(now: Long = System.currentTimeMillis()): String =
        profileContext(profile(AUTHOR_HER), profile(AUTHOR_ME), posts(), now)
}

/** Лайки и комментарии Гесперы или Эст к постам Адониса и Авроры: сервер присылает их в changes (field — id поста). */
object PageSync {
    fun apply(ctx: Context, changes: List<HesperaChange>, notify: Boolean = true, who: String = WHO_HESPERA) {
        val ps = ProfileStore(ctx)
        var touched = false
        for (c in changes) {
            if (c.field.isBlank()) continue
            when (c.kind) {
                "лайк" -> { ps.setLike(c.field, who, true); touched = true }
                "коммент" -> {
                    val before = ps.posts().firstOrNull { it.id == c.field }?.comments?.size ?: continue
                    val p = ps.addComment(c.field, who, c.value) ?: continue
                    touched = true
                    if (notify && p.comments.size > before && p.author == AUTHOR_ME) {
                        val text = "${whoName(who)} под твоим постом: ${c.value.take(120)}"
                        if (who == WHO_EST) EstNotifications.show(ctx, text, target = "feed") else HesperaNotifications.show(ctx, text, target = "feed")
                    }
                }
            }
        }
        if (touched) PageBus.signal()
    }
}

/** Лента поменялась не с экрана (Геспера, фоновые пробуждения): экраны перечитывают посты. */
object PageBus {
    val flow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    fun signal() { flow.tryEmit(Unit) }
}
