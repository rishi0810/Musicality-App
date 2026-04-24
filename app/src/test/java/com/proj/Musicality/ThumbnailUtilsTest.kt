package com.proj.Musicality

import com.proj.Musicality.util.upscaleThumbnail
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailUtilsTest {

    @Test
    fun upscaleThumbnail_onlyRewritesTransformParams() {
        val original =
            "https://yt3.googleusercontent.com/dqBTdcg4xXJSwSZNm_zeMK55hSeC49-VkgKEpspC3te3lCt1TdCmO6-IVHKtFnp0jN-h8A23a-K_-F_RoQ=w120-h120-l90-rj"

        val expected =
            "https://yt3.googleusercontent.com/dqBTdcg4xXJSwSZNm_zeMK55hSeC49-VkgKEpspC3te3lCt1TdCmO6-IVHKtFnp0jN-h8A23a-K_-F_RoQ=w544-h544-l90-rj"

        assertEquals(expected, upscaleThumbnail(original))
    }

    @Test
    fun upscaleThumbnail_rewritesSquareTransformParam() {
        val original = "https://lh3.googleusercontent.com/example-id=s120-c-k-c0x00ffffff-no-rj"

        val expected = "https://lh3.googleusercontent.com/example-id=s544-c-k-c0x00ffffff-no-rj"

        assertEquals(expected, upscaleThumbnail(original))
    }
}
