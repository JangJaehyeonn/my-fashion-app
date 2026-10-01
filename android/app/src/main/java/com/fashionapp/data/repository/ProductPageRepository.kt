package com.fashionapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ProductPage(
    val title: String?,
    val imageUrls: List<String>
)

data class DownloadedImage(
    val bytes: ByteArray,
    val mimeType: String
)

/**
 * 쇼핑몰 상품 페이지에서 대표 이미지 후보를 뽑는다.
 * 서버가 아니라 앱에서 직접 요청하는 이유: 사용자가 입력한 임의 URL을 EC2가 대신 요청하면
 * 내부망/메타데이터 주소를 찌르는 SSRF 통로가 되기 때문.
 * ⚠️ ApiClient의 OkHttpClient를 쓰면 외부 사이트에 JWT가 붙어 나가므로 인증 없는 전용 클라이언트를 쓴다.
 */
@Singleton
class ProductPageRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 공유 링크(onelink.me) 해석 전용 — 리다이렉트를 자동으로 따라가지 않고 매 단계 도메인을 검증하며 따라간다
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun fetchProductPage(rawUrl: String): Result<ProductPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = resolveShareLink(normalizeUrl(rawUrl) ?: error("올바른 상품 페이지 주소가 아니에요."))
            // 리다이렉트(예: 단축 URL, 모바일 페이지) 후 최종 주소 기준으로 상대 경로를 풀어야 함
            val (finalUrl, html) = execute(url).use { response ->
                if (!response.isSuccessful) error("페이지를 불러오지 못했어요. (HTTP ${response.code})")
                response.request.url to readLimited(response, MAX_HTML_BYTES).toString(Charsets.UTF_8)
            }
            val images = extractImageUrls(html)
                .mapNotNull { resolve(finalUrl, it) }
                .distinct()
                .take(MAX_CANDIDATES)
            if (images.isEmpty()) error("이 페이지에서 옷 이미지를 찾지 못했어요. 갤러리에서 옷 사진을 골라주세요.")
            ProductPage(title = extractTitle(html), imageUrls = images)
        }
    }

    suspend fun downloadImage(imageUrl: String): Result<DownloadedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = imageUrl.toHttpUrlOrNull() ?: error("이미지 주소가 올바르지 않아요.")
            execute(url).use { response ->
                if (!response.isSuccessful) error("옷 이미지를 내려받지 못했어요. (HTTP ${response.code})")
                val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                if (mimeType !in ALLOWED_IMAGE_TYPES) error("지원하지 않는 이미지 형식이에요. ($mimeType)")
                DownloadedImage(readLimited(response, MAX_IMAGE_BYTES), mimeType!!)
            }
        }
    }

    /**
     * 앱 공유 링크(AppsFlyer OneLink, 예: musinsa.onelink.me/xxxx/yyyy)를 실제 상품 페이지 URL로 바꾼다.
     * 모바일 UA로 요청하면 리다이렉트 없이 JS로 앱 실행(intent://)만 시도하는 중계 HTML(og:image 없음)이 오고,
     * 데스크톱 UA로 요청하면 웹 상품 페이지로 301 리다이렉트되므로 데스크톱 UA로 한 단계씩 따라간다.
     * 따라가는 주소는 https + (onelink.me 또는 허용된 쇼핑몰 도메인)으로 제한하고, 최대 [MAX_SHARE_HOPS]번만 따라간다.
     * onelink가 아닌 일반 상품 URL은 그대로 반환한다.
     */
    private fun resolveShareLink(start: HttpUrl): HttpUrl {
        if (!isOneLinkHost(start.host)) return start
        var current = start
        repeat(MAX_SHARE_HOPS) {
            if (!isOneLinkHost(current.host)) return current
            val next = noRedirectClient.newCall(
                Request.Builder()
                    .url(current)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .build()
            ).execute().use { response ->
                if (!response.isRedirect) error(SHARE_LINK_UNSUPPORTED)
                val location = response.header("Location")?.let { response.request.url.resolve(it) }
                    ?: error(SHARE_LINK_UNSUPPORTED)
                if (location.scheme != "https" || !(isOneLinkHost(location.host) || isAllowedShopHost(location.host))) {
                    error(SHARE_LINK_UNSUPPORTED)
                }
                location
            }
            current = next
        }
        // 여전히 onelink 중계 단계에 머물러 있으면 해석 실패
        if (isOneLinkHost(current.host)) error(SHARE_LINK_UNSUPPORTED)
        return current
    }

    private fun isOneLinkHost(host: String) = host == "onelink.me" || host.endsWith(".onelink.me")

    private fun isAllowedShopHost(host: String) =
        ALLOWED_SHOP_DOMAINS.any { host == it || host.endsWith(".$it") }

    private fun execute(url: HttpUrl): Response =
        client.newCall(
            Request.Builder()
                .url(url)
                // 일부 쇼핑몰은 기본 okhttp UA를 봇으로 보고 막으므로 모바일 브라우저 UA를 쓴다
                .header("User-Agent", MOBILE_UA)
                .header("Accept-Language", "ko-KR,ko;q=0.9")
                .build()
        ).execute()

    private fun readLimited(response: Response, limit: Long): ByteArray {
        val body = response.body ?: error("응답이 비어 있어요.")
        val source = body.source()
        source.request(limit + 1)
        if (source.buffer.size > limit) error("파일이 너무 커요.")
        return source.buffer.readByteArray()
    }

    private fun normalizeUrl(raw: String): HttpUrl? {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return withScheme.toHttpUrlOrNull()
    }

    private fun resolve(base: HttpUrl, candidate: String): String? {
        val cleaned = decodeEntities(candidate.trim())
        if (cleaned.isEmpty() || cleaned.startsWith("data:")) return null
        val resolved = if (cleaned.startsWith("//")) "${base.scheme}:$cleaned".toHttpUrlOrNull() else base.resolve(cleaned)
        return resolved?.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
    }

    private fun extractImageUrls(html: String): List<String> {
        val metaImages = META_TAG.findAll(html).mapNotNull { tag ->
            val key = attr(tag.value, "property") ?: attr(tag.value, "name") ?: return@mapNotNull null
            if (key.lowercase() in IMAGE_META_KEYS) attr(tag.value, "content") else null
        }.toList()
        val linkImages = LINK_TAG.findAll(html)
            .filter { attr(it.value, "rel")?.lowercase() == "image_src" }
            .mapNotNull { attr(it.value, "href") }
            .toList()
        // JSON-LD Product의 "image": "..." 또는 "image": ["...", ...]
        val jsonLdImages = JSON_LD.findAll(html).flatMap { block ->
            JSON_IMAGE.findAll(block.groupValues[1]).flatMap { match ->
                JSON_STRING.findAll(match.groupValues[1]).map { it.groupValues[1].replace("\\/", "/") }
            }
        }.toList()
        return metaImages + linkImages + jsonLdImages
    }

    private fun extractTitle(html: String): String? {
        val ogTitle = META_TAG.findAll(html).firstNotNullOfOrNull { tag ->
            val key = attr(tag.value, "property") ?: attr(tag.value, "name")
            if (key.equals("og:title", ignoreCase = true)) attr(tag.value, "content") else null
        }
        val title = ogTitle ?: TITLE_TAG.find(html)?.groupValues?.get(1)
        // "상품명 - 사이즈 & 후기 | 무신사"처럼 붙는 사이트명 꼬리를 잘라 VTON 옷 설명으로 쓰기 좋게 정리
        return title?.let { decodeEntities(it).substringBefore(" | ").removeSuffix(" - 사이즈 & 후기").trim() }
            ?.takeIf { it.isNotEmpty() }
            ?.take(100)
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    companion object {
        private const val MAX_CANDIDATES = 6
        private const val MAX_SHARE_HOPS = 3
        // 공유 링크 해석 중 따라가도 되는 쇼핑몰 도메인 (서브도메인 포함)
        private val ALLOWED_SHOP_DOMAINS = listOf("musinsa.com", "29cm.co.kr", "zigzag.kr", "a-bly.com")
        private const val SHARE_LINK_UNSUPPORTED = "앱 공유 링크는 지원하지 않아요. 브라우저에서 상품 페이지 주소를 복사해 주세요."
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val MAX_HTML_BYTES = 3L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024 // AI 서버 /ai/vton 한도와 동일
        // AI 서버 /ai/vton 허용 형식과 동일
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        private val IMAGE_META_KEYS = setOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val TITLE_TAG = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val JSON_LD = Regex(
            """<script[^>]*application/ld\+json[^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val JSON_IMAGE = Regex(""""image"\s*:\s*(\[[^\]]*]|"[^"]*")""")
        private val JSON_STRING = Regex(""""((?:[^"\\]|\\.)*)"""")
    }
}
