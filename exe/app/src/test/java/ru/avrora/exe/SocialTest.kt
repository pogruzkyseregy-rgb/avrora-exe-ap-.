package ru.avrora.exe

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Лента: поиск поста по словам, комментарии, лайки Гесперы, снимок для сервера. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SocialTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun fresh(): ProfileStore {
        File(ctx.filesDir, "exe_posts.json").delete()
        File(ctx.filesDir, "exe_profiles.json").delete()
        return ProfileStore(ctx)
    }

    @Test fun commentMarkerParsed() {
        val parts = parseParts("Ну и пост.\n[коммент: новый стан | гудит громче меня]")
        val c = parts.filterIsInstance<Part.Comment>().single()
        assertEquals("новый стан", c.snippet)
        assertEquals("гудит громче меня", c.text)
        assertEquals(Part.Text("Ну и пост."), parts.first())
    }

    @Test fun findPostByWords() {
        val posts = listOf(
            PostItem("a", AUTHOR_ME, "Сегодня запустили новый стан, гудит как зверь", "", 1),
            PostItem("b", AUTHOR_HER, "Ночью лисы не спят, они думают о звёздах", "", 2)
        )
        assertEquals("a", findPost("новый стан", posts)?.id)
        assertEquals("b", findPost("ЛИСЫ НЕ СПЯТ", posts)?.id)
        assertEquals("b", findPost("лисы ночью думают про звёзды", posts)?.id)
        assertNull(findPost("рецепт борща", posts))
        assertNull(findPost("новый стан", posts, AUTHOR_HER))
    }

    @Test fun commentsStoredOnceAndDeleted() {
        val ps = fresh()
        ps.addPost(PostItem("p1", AUTHOR_ME, "Пост", "", 1))
        assertNotNull(ps.addComment("p1", AUTHOR_HER, "  привет   тебе ", 10))
        ps.addComment("p1", AUTHOR_HER, "привет тебе", 11)
        ps.addComment("p1", AUTHOR_ME, "и тебе", 12)
        val cs = ps.posts().single().comments
        assertEquals(listOf("привет тебе", "и тебе"), cs.map { it.text })
        ps.removeComment("p1", AUTHOR_ME, 12)
        assertEquals(1, ps.posts().single().comments.size)
        assertNull(ps.addComment("nope", AUTHOR_ME, "x"))
    }

    @Test fun hesperaChangesApplied() {
        val ps = fresh()
        ps.addPost(PostItem("p1", AUTHOR_ME, "Мой пост", "", 1))
        PageSync.apply(ctx, listOf(
            HesperaChange("лайк", "p1", "Мой пост", ""),
            HesperaChange("коммент", "p1", "красиво", "к посту: Мой пост"),
            HesperaChange("коммент", "", "под своим", "к своему посту"),
            HesperaChange("коммент", "unknown", "мимо", "")
        ), notify = false)
        val p = ps.posts().single()
        assertTrue(p.likedByHesp)
        assertEquals(listOf(PostComment(WHO_HESPERA, "красиво", p.comments[0].at)), p.comments)
    }

    @Test fun pageSnapshot() {
        val ps = fresh()
        ps.setStatus(AUTHOR_ME, "на смене", 1)
        ps.addPost(PostItem("p1", AUTHOR_ME, "Мой пост", "снимок:x.jpg", 5, likedByHer = true,
            comments = listOf(PostComment(AUTHOR_HER, "ого", 6))))
        val j = ps.pageJson()
        assertEquals("на смене", j.getString("adonis_status"))
        val p = j.getJSONArray("posts").getJSONObject(0)
        assertEquals("p1", p.getString("id"))
        assertTrue(p.getBoolean("photo"))
        assertEquals("aurora", p.getJSONArray("liked_by").getString(0))
        assertEquals("ого", p.getJSONArray("comments").getJSONObject(0).getString("text"))
    }

    @Test fun auroraSeesCommentsAndHesperaLikes() {
        val t = profileContext(Profile(), Profile(), listOf(
            PostItem("p1", AUTHOR_ME, "Мой пост", "", 1, likedByHesp = true,
                comments = listOf(PostComment(WHO_HESPERA, "красиво", 2), PostComment(AUTHOR_HER, "согласна", 3)))
        ))
        assertTrue(t, t.contains("♥ Геспера"))
        assertTrue(t, t.contains("Геспера: красиво / я: согласна"))
        assertTrue(t, t.contains("[коммент:"))
    }

    @Test fun repeatedAnsweredQuestionIsCaught() {
        val ask1 = "Аврора. Я спросила тебя ночью — «как ты на самом деле» — и уснула, не дождавшись ответа. Так что спрашиваю ещё раз: ты ответила не колкостью — и что было дальше? Хочешь, чтоб я не лезла, — скажи, я отойду. Но молчание твоё я фальшивкой считать не буду."
        val ask2 = "Аврора. Я вчера дошла до того, что спросила тебя без задания, и уснула, не дождавшись. Теперь спрашиваю снова, но в общем. Не хочешь отвечать здесь — скажи, и я отойду. Молчание твоё фальшивкой не посчитаю."
        val g = listOf(ChatMessage(WHO_HESPERA, ask1, 1), ChatMessage(WHO_AURORA, "Не лезь, это было в личке.", 2))
        assertTrue(repeatsAnswered(ask2, g, WHO_HESPERA))
        assertTrue(!repeatsAnswered(ask2, g.take(1), WHO_HESPERA))
        assertTrue(!repeatsAnswered("Аврора, а ночной сканер опять стучался в порт двадцать два, ты видела логи сегодня?", g, WHO_HESPERA))
    }
}
