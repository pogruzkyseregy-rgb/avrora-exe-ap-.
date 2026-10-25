package ru.avrora.exe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Партия и ответы на приглашения: файл exe_game.json. Партия переживает закрытие приложения. */
class GameStore(ctx: Context) {
    private val file = File(ctx.filesDir, "exe_game.json")

    data class Saved(val game: GameState?, val invites: Map<Long, String>, val recorded: Set<String>)

    fun load(): Saved {
        if (!file.exists()) return Saved(null, emptyMap(), emptySet())
        return runCatching {
            val o = JSONObject(file.readText())
            val inv = HashMap<Long, String>()
            o.optJSONObject("invites")?.let { j -> j.keys().forEach { k -> inv[k.toLong()] = j.getString(k) } }
            val rec = HashSet<String>()
            o.optJSONArray("recorded")?.let { a -> for (i in 0 until a.length()) rec.add(a.getString(i)) }
            // партия со старого поля (до выбора фишек) не продолжается: у фишек нет мест на новом поле
            val g = o.optJSONObject("game")?.let { gameFromJson(it) }
                ?.takeIf { it.over || it.phase == "order" || it.phase == "pick" || it.players.all { p -> p.token >= 0 } }
            Saved(g, inv, rec)
        }.getOrDefault(Saved(null, emptyMap(), emptySet()))
    }

    fun save(saved: Saved) {
        val o = JSONObject()
        saved.game?.let { o.put("game", gameToJson(it)) }
        val inv = JSONObject()
        saved.invites.entries.toList().takeLast(50).forEach { inv.put(it.key.toString(), it.value) }
        o.put("invites", inv)
        o.put("recorded", JSONArray(saved.recorded.toList().takeLast(50)))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    private fun gameToJson(g: GameState): JSONObject = JSONObject()
        .put("id", g.id).put("laps", g.laps).put("turn", g.turn).put("first", g.first).put("phase", g.phase)
        .put("finalRound", g.finalRound).put("winner", g.winner).put("startedAt", g.startedAt).put("turnNo", g.turnNo).put("seq", g.seq)
        .put("dice", JSONArray(g.dice))
        .put("log", JSONArray(g.log))
        .put("owners", JSONObject().also { j -> g.owners.forEach { (k, v) -> j.put(k.toString(), v) } })
        .put("players", JSONArray().also { a ->
            g.players.forEach {
                a.put(JSONObject().put("who", it.who).put("pos", it.pos).put("money", it.money).put("laps", it.laps)
                    .put("skip", it.skip).put("quarantine", it.quarantine).put("shield", it.shield).put("token", it.token))
            }
        })

    private fun gameFromJson(o: JSONObject): GameState {
        val pa = o.getJSONArray("players")
        val players = (0 until pa.length()).map { i ->
            val p = pa.getJSONObject(i)
            GPlayer(p.getString("who"), p.optInt("pos"), p.optInt("money", START_MONEY), p.optInt("laps"),
                p.optInt("skip"), p.optInt("quarantine"), p.optBoolean("shield"), p.optInt("token", -1))
        }
        val owners = HashMap<Int, Int>()
        o.optJSONObject("owners")?.let { j -> j.keys().forEach { k -> owners[k.toInt()] = j.getInt(k) } }
        val dice = o.optJSONArray("dice")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList()
        val log = o.optJSONArray("log")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return GameState(
            o.getString("id"), o.optInt("laps", GAME_DEFAULT_LAPS), players, owners, o.optInt("turn"), o.optInt("first"),
            o.optString("phase", "order"), dice, log, o.optBoolean("finalRound"), o.optInt("winner", -1),
            o.optLong("startedAt"), o.optInt("turnNo"), o.optInt("seq")
        )
    }
}
