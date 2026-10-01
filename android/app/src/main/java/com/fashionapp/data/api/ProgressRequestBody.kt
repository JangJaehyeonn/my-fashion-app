package com.fashionapp.data.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** 요청 본문이 소켓으로 나가는 만큼(written/total 바이트) 알려주는 래퍼 — 업로드 진행률 표시용 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (written: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting = object : ForwardingSink(sink) {
            private var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
