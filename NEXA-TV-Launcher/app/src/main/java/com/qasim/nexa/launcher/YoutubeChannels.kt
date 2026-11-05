package com.qasim.nexa.launcher

/**
 * Curated official/public channels for Urdu- and Hindi-language viewers.
 * IDs are stable YouTube channel IDs used by the public Atom uploads feed.
 */
internal data class YoutubeChannel(
    val name: String,
    val channelId: String
)

internal object YoutubeChannelCatalog {
    val categories: Map<String, List<YoutubeChannel>> = linkedMapOf(
        "Pakistani Dramas" to listOf(
            YoutubeChannel("HUM TV", "UCEeEQxm6qc_qaTE7qTV5aLQ"),
            YoutubeChannel("ARY Digital", "UC4JCksJF76g_MdzPVBJoC3Q"),
            YoutubeChannel("Har Pal Geo", "UCe9JSDmyqNgA_l2BzGHq1Ug"),
            YoutubeChannel("Green TV Entertainment", "UCF2ltLX1dnPa6-L1paPaMzw")
        ),
        "Indian Movies" to listOf(
            YoutubeChannel("Shemaroo Movies", "UCBOmfqgTZi7yDp4-3Lr_3lA"),
            YoutubeChannel("Goldmines", "UCyoXW-Dse7fURq30EWl_CUA"),
            YoutubeChannel("Ultra Movie Parlour", "UCYauDsl-rswjQGpA0_-Z9Pw"),
            YoutubeChannel("Rajshri", "UCEKWXRsfUHkan-D_ljU8Asw")
        ),
        "Indian TV & Dramas" to listOf(
            YoutubeChannel("SET India", "UCpEhnqL0y41EpW2TvWAHD7Q"),
            YoutubeChannel("Colors TV", "UC55IWqFLDH1Xp7iu1_xknRA")
        ),
        "Hindi/Urdu Technology" to listOf(
            YoutubeChannel("Technical Guruji", "UCOhHO2ICt0ti9KAh-QHvttQ"),
            YoutubeChannel("Trakin Tech", "UCEPL07qzVsOcHd3sMUws65g"),
            YoutubeChannel("Technology Gyan", "UC1tVU8H153ZFO9eRsxdJlhA"),
            YoutubeChannel("Gyan Therapy", "UCS261lQDXjMvc8Jck-2CQEg"),
            YoutubeChannel("Techno Ruhez", "UC8vlDm5xvbxOrCvdu9UNOiA"),
            YoutubeChannel("Tech Burner", "UCXUJJNoP1QupwsYIWFXmsZg"),
            YoutubeChannel("Hasnain Technical", "UCxE248p2-PE6PK-AATg9mcg")
        ),
        "Hindi/Urdu Gaming" to listOf(
            YoutubeChannel("Techno Gamerz", "UCX8pnu3DYUnx8qy8V_c6oHg"),
            YoutubeChannel("Total Gaming", "UC5c9VlYTSvBSCaoMu_GI6gQ"),
            YoutubeChannel("CarryisLive", "UC0IWRLai-BAwci_e9MylNGw"),
            YoutubeChannel("Star Anonymous", "UCueAzFc4gCEJfAjQy1kcN3g"),
            YoutubeChannel("AS Gaming", "UCMrvxKTx9hLhZcOvJkYOnAw"),
            YoutubeChannel("Gyan Gaming", "UChXi_PlJkRMPYFQBOJ3MpxA"),
            YoutubeChannel("Lokesh Gamer", "UCdQPeeJ0qGK6wWBiEJWcdsQ"),
            YoutubeChannel("PUBG MOBILE Pakistan Official", "UCr5-mYyayqz7iQR3TzcoDng")
        ),
        "Pakistani Vlogs" to listOf(
            YoutubeChannel("Ducky Bhai", "UC_c-RTowPbIlzMkIa_O7s6Q"),
            YoutubeChannel("Maaz Safder World", "UCz0M2pV4jOUUQ88lOd2q08A"),
            YoutubeChannel("Shahveer Jafry", "UChIrj5Qb5V-TZIRrk0Df-Uw"),
            YoutubeChannel("Zaid Ali T", "UCVBXum_ntfgHgHCOYtGOmEw"),
            YoutubeChannel("Rabeeca Khan", "UCfPL_q6AOLp0o8sP7t6FbXA")
        ),
        "Indian Vlogs" to listOf(
            YoutubeChannel("Sourav Joshi Vlogs", "UCjvgGbPPn-FgYeguc5nxG4A"),
            YoutubeChannel("Flying Beast", "UCNSdjX4ry9fICqeObdZPAZQ")
        )
    )

    val categoryNames: List<String>
        get() = categories.keys.toList()

    fun channelsFor(category: String): List<YoutubeChannel> =
        categories[category].orEmpty()

    val uniqueChannelCount: Int
        get() = categories.values.flatten().distinctBy { it.channelId }.size
}
