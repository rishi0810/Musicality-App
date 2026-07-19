package com.proj.Musicality

import com.proj.Musicality.data.parser.NextParser
import com.proj.Musicality.data.parser.RelatedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RelatedParserTest {
    @Test
    fun extractsRelatedBrowseIdFromNamedTab() {
        val response = """
            {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"title":"Up next"}},{"tabRenderer":{"title":"Related","endpoint":{"browseEndpoint":{"browseId":"MPTRt_vLf0KMwxMrR-1"}}}}]}}}}}
        """.trimIndent()

        assertEquals("MPTRt_vLf0KMwxMrR-1", NextParser.extractRelatedBrowseId(response))
    }

    @Test
    fun extractsRelatedShelvesAndArtistDescription() {
        val response = """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "musicCarouselShelfRenderer": {
                        "header": {
                          "musicCarouselShelfBasicHeaderRenderer": {
                            "title": {"runs": [{"text": "Similar artists"}]}
                          }
                        },
                        "contents": [
                          {
                            "musicTwoRowItemRenderer": {
                              "title": {"runs": [{"text": "Myles Smith"}]},
                              "navigationEndpoint": {
                                "browseEndpoint": {
                                  "browseId": "UC8smQAQdbRbjv1SmDirCLZg",
                                  "browseEndpointContextSupportedConfigs": {
                                    "browseEndpointContextMusicConfig": {
                                      "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                                    }
                                  }
                                }
                              }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "musicDescriptionShelfRenderer": {
                        "header": {"runs": [{"text": "About the artist"}]},
                        "description": {"runs": [{"text": "Artist biography"}]}
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val feed = RelatedParser.extractFeed(response)
        val card = feed.sections.single().items.single()

        assertEquals("Similar artists", feed.sections.single().title)
        assertNotNull(card)
        assertEquals("Artist biography", feed.aboutArtist)
    }
}
