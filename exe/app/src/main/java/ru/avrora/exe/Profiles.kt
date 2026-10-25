package ru.avrora.exe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------- профили, лента, лайки, статусы ----------
// Лента видна только вам двоим, она живёт в приложении. Аврора пишет в неё маркерами
// [пост: текст], [пост: текст | фото:id], [статус: текст], [лайк: слова из поста], [аватар: id фото].

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
            PostItem(
                o.optString("id"), o.optString("author"), o.optString("text"), o.optString("photo"),
                o.optLong("at"), o.optBoolean("likedByMe"), o.optBoolean("likedByHer")
            )
        }
    }

    private fun savePosts(list: List<PostItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("author", it.author).put("text", it.text).put("photo", it.photo)
                    .put("at", it.at).put("likedByMe", it.likedByMe).put("likedByHer", it.likedByHer)
            )
        }
        write("exe_posts.json", a.toString())
    }

    @Synchronized
    fun addPost(p: PostItem) = savePosts((posts() + p).takeLast(200))

    @Synchronized
    fun removePost(id: String) = savePosts(posts().filter { it.id != id })

    /** who: AUTHOR_ME или AUTHOR_HER. */
    @Synchronized
    fun setLike(id: String, who: String, on: Boolean) = savePosts(posts().map {
        if (it.id != id) it else if (who == AUTHOR_ME) it.copy(likedByMe = on) else it.copy(likedByHer = on)
    })

    @Synchronized
    fun countSince(author: String, since: Long): Int = posts().count { it.author == author && it.at >= since }

    /** Текст для неё: что сейчас на страницах и в ленте. */
    fun context(now: Long = System.currentTimeMillis()): String =
        profileContext(profile(AUTHOR_HER), profile(AUTHOR_ME), posts(), now)
}
