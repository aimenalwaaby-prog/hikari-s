package com.hikari.app.manga

import com.hikari.app.data.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaPageRepositoryTest {
    @Test
    fun `filters covers previews blanks and duplicate CDN query variants`() {
        val pages = listOf(
            StreamSource(name = "blank", url = ""),
            StreamSource(name = "chapter", url = "https://site.example/manga/chapter-4"),
            StreamSource(name = "cover", url = "https://img.example/cover.jpg"),
            StreamSource(name = "thumb", url = "https://img.example/thumb-page-1.jpg"),
            StreamSource(name = "preview", url = "https://img.example/page-preview.jpg"),
            StreamSource(name = "page 1", url = "https://img.example/p1.jpg?token=first", pageUrl = "https://site.example/page/1"),
            StreamSource(name = "page 1 duplicate", url = "https://img.example/p1.jpg?token=refreshed", pageUrl = "https://site.example/page/1"),
            StreamSource(name = "page 2", url = "https://img.example/p2.jpg", pageUrl = "https://site.example/page/2"),
        )

        val result = MangaPageRepository.filter(
            pages,
            chapterUrl = "https://site.example/manga/chapter-4",
            posterUrl = "https://img.example/cover.jpg",
        )

        assertEquals(listOf("page 1", "page 2"), result.map { it.name })
        assertTrue(result.all { it.url.isNotBlank() })
    }

    @Test
    fun `preserves page order headers and page reference`() {
        val first = StreamSource(
            name = "p1",
            url = "https://cdn.example/one.jpg",
            headers = mapOf("Referer" to "https://site.example/chapter"),
            pageUrl = "https://site.example/page/1",
        )
        val second = StreamSource(
            name = "p2",
            url = "https://cdn.example/two.jpg",
            pageUrl = "https://site.example/page/2",
        )

        val result = MangaPageRepository.filter(listOf(first, second), "chapter-url", "")

        assertEquals(listOf("p1", "p2"), result.map { it.name })
        assertEquals(first.headers, result.first().headers)
        assertEquals(first.pageUrl, result.first().pageUrl)
    }

    @Test
    fun `stable keys ignore rotating query tokens but distinguish pages`() {
        val first = StreamSource(name = "p1", url = "https://cdn.example/p1.jpg?token=old", pageUrl = "https://site.example/p1?session=old")
        val refreshed = StreamSource(name = "p1", url = "https://cdn.example/p1.jpg?token=new", pageUrl = "https://site.example/p1?session=new")
        val second = StreamSource(name = "p2", url = "https://cdn.example/p2.jpg?token=new", pageUrl = "https://site.example/p2")

        assertEquals(MangaPageRepository.stableKey(first), MangaPageRepository.stableKey(refreshed))
        assertNotEquals(MangaPageRepository.stableKey(first), MangaPageRepository.stableKey(second))
    }
}
