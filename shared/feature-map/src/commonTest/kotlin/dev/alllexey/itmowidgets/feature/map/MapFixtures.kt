package dev.alllexey.itmowidgets.feature.map

import okio.ByteString.Companion.encodeUtf8

/** A two-building mirror in the mirror's own formats: 13 with rooms 2403 and the entrance, 273 with 1124. */
internal object MapFixtures {
    // Graph of 13: entrance 469 (node 1) - corridor 5 (node 2) - stairs - 2403 (node 4) on level 3.
    val GRAPH_13 = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3090,59.9560,0]},"properties":{"id":1,"area_id":469,"level":0,"transitions":[{"id":2,"distance":3,"type":"straight","weight":5}]}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3091,59.9561,0]},"properties":{"id":2,"area_id":5,"level":0,"transitions":[{"id":1,"distance":3,"type":"straight","weight":5},{"id":3,"distance":5,"type":"stairs","weight":20}]}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3091,59.9561,3]},"properties":{"id":3,"area_id":5,"level":3,"transitions":[{"id":2,"distance":5,"type":"stairs","weight":1},{"id":4,"distance":4,"type":"straight","weight":5}]}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3092,59.9562,3]},"properties":{"id":4,"area_id":71,"level":3,"transitions":[{"id":3,"distance":4,"type":"straight","weight":5}]}}
        ]}
    """.trimIndent()

    val ROOMS_13 = """
        [{"id":"469","level":"0","class":"room","subclass":"entrance","name":"Вход","lngLat":[30.3090,59.9560]},
         {"id":"71","level":"3","class":"room","subclass":"education","name":"2403","ref":"2403","isu:room_id":"59","lngLat":[30.3092,59.9562]},
         {"id":"77","level":"0","class":"room","subclass":"restroom.male","lngLat":[30.3093,59.9563]},
         {"id":"5","level":"0","class":"corridor","subclass":"corridor","name":"1 этаж","lngLat":[30.3091,59.9561]}]
    """.trimIndent()

    val GRAPH_273 = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3380,59.9266,0]},"properties":{"id":1,"area_id":900,"level":0,"transitions":[{"id":2,"distance":3,"type":"straight","weight":5}]}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[30.3381,59.9267,0]},"properties":{"id":2,"area_id":1124,"level":0,"transitions":[{"id":1,"distance":3,"type":"straight","weight":5}]}}
        ]}
    """.trimIndent()

    val ROOMS_273 = """
        [{"id":"900","level":"0","class":"room","subclass":"entrance","name":"Вход","lngLat":[30.3380,59.9266]},
         {"id":"1124","level":"0","class":"room","subclass":"education","name":"1124","ref":"1124","lngLat":[30.3381,59.9267]}]
    """.trimIndent()

    const val TILES = "not really sqlite"

    fun sha(text: String) = text.encodeUtf8().sha256().hex()

    /** Path on the mirror to body. */
    val FILES: Map<String, String> = listOf(GRAPH_13, ROOMS_13, GRAPH_273, ROOMS_273, TILES).associateBy { "files/${sha(it)}.${if (it == TILES) "mbtiles" else "json"}" }

    private fun file(text: String, ext: String) = """{"file":"files/${sha(text)}.$ext","sha256":"${sha(text)}","bytes":${text.length}}"""

    val MANIFEST = """
        {"schema":1,"attribution":"План: Университет ИТМО","glyphs":"glyphs/{fontstack}/{range}.pbf","buildings":[
          {"id":13,"name":"Кронверкский пр., 49","bounds":[30.307,59.955,30.311,59.957],"center":[30.3095,59.9567],"bearing":160,"levels":[0,1,2,3,4],
           "tiles":${file(TILES, "mbtiles")},"graph":${file(GRAPH_13, "json")},"rooms":${file(ROOMS_13, "json")}},
          {"id":273,"name":"Ломоносова ул., 9","bounds":[30.336,59.925,30.339,59.927],"center":[30.338,59.9266],"bearing":216,"levels":[0,1],
           "tiles":${file(TILES, "mbtiles")},"graph":${file(GRAPH_273, "json")},"rooms":${file(ROOMS_273, "json")}}
        ]}
    """.trimIndent()
}
