package eu.kanade.tachiyomi.extension.ko.jmana

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

/**
 * JMana Source
 **/
class JMana : ParsedHttpSource() {
    override val name = "JMana"
    override val baseUrl = "https://mangahide.com"
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaSelector() = "div.conts > ul > li"

    override fun popularMangaFromElement(element: Element): SManga {
        val linkElement = element.select("a")
        val titleElement = element.select(".titBox > span").first()
        val link = linkElement.attr("href")
                .replace(" ", "%20")
                .replace(Regex("/[0-9]+(?!.*?/)"), "")

        val manga = SManga.create()
        manga.setUrlWithoutDomain(link)
        manga.title = titleElement.text()
        manga.thumbnail_url = baseUrl + element.select(".imgBox img").attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.page > ul > li"

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic_main_frame?page=${page - 1}")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        // Can not detect what page is last page but max mangas are 40.
        val hasNextPage = mangas.size == 40

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comic_main_frame?keyword=$query&page=${page - 1}")


    override fun mangaDetailsParse(document: Document): SManga {
        val info = document.select("div.leftM").first()
        val authorText = info.select("div.comBtnArea a").text()
        val titleDescription = info.select("li.row")

        val manga = SManga.create()
        manga.title = titleDescription.first().text()
        manga.description = titleDescription.last().text()
        manga.author = authorText
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun chapterListSelector() = "div.contents > ul > li"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select("a")
        val rawName = linkElement.text()

        val chapter = SChapter.create()
        chapter.url = linkElement.attr("href").replace("book/", "book_frame/")
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName.trim()
        chapter.date_upload = parseChapterDate(element.select("ul > li:not(.fcR)").last().text())
        return chapter
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd").parse(date).time
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            document.select(".view li#view_content2")
                    .map { it.select("div img").attr("src") }
                    .forEach { pages.add(Page(pages.size, "", it)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/frame")
    override fun latestUpdatesNextPageSelector() = ""
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = false

        return MangasPage(mangas, hasNextPage)
    }


    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()
}