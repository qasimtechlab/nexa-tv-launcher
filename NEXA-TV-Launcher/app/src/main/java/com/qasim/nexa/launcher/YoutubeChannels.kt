package com.qasim.nexa.launcher

/**
 * Curated channel catalogue for Pakistani, Indian and wider Hindi/Urdu audiences.
 *
 * Values beginning with "@" are YouTube handles. YoutubeFeed resolves these to
 * their stable UC channel IDs and caches the result. This makes the catalogue
 * easier to expand without hard-coding opaque IDs for every channel.
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
            YoutubeChannel("Green TV Entertainment", "UCF2ltLX1dnPa6-L1paPaMzw"),
            YoutubeChannel("Har Pal Geo", "@HarPalGeoOfficial"),
            YoutubeChannel("ARY Digital HD", "@ARYDigitalasia"),
            YoutubeChannel("HUM TV", "@HUMTV"),
            YoutubeChannel("Green TV Entertainment", "@GreenEntertainmentTV"),
            YoutubeChannel("Zem TV", "@zemtv.official"),
            YoutubeChannel("Pyaar Lafzon Mein Kahan", "@pyaarlafzonmeinkahan"),
            YoutubeChannel("Fairy Tales Urdu", "@fairytalesurdu")
        ),
        "Indian Movies" to listOf(
            YoutubeChannel("Shemaroo Movies", "UCBOmfqgTZi7yDp4-3Lr_3lA"),
            YoutubeChannel("Goldmines", "UCyoXW-Dse7fURq30EWl_CUA"),
            YoutubeChannel("Ultra Movie Parlour", "UCYauDsl-rswjQGpA0_-Z9Pw"),
            YoutubeChannel("Rajshri", "UCEKWXRsfUHkan-D_ljU8Asw"),
            YoutubeChannel("YRF", "@yrf"),
            YoutubeChannel("Shemaroo", "@shemaroo"),
            YoutubeChannel("Dangal TV", "@dangaltvchannel"),
            YoutubeChannel("Sri Balaji Movies", "@sribalajimovies"),
            YoutubeChannel("Bollywood Classics", "@bollywoodclassics"),
            YoutubeChannel("StarPlus", "@starplus"),
            YoutubeChannel("Ishtar Music", "@ishtarmusic")
        ),
        "Indian TV & Dramas" to listOf(
            YoutubeChannel("SET India", "UCpEhnqL0y41EpW2TvWAHD7Q"),
            YoutubeChannel("Colors TV", "UC55IWqFLDH1Xp7iu1_xknRA"),
            YoutubeChannel("SET India", "@setindia"),
            YoutubeChannel("Sony SAB", "@sonysab"),
            YoutubeChannel("Zee TV", "@zeetv"),
            YoutubeChannel("Colors TV", "@colorstv"),
            YoutubeChannel("ETV Telugu", "@etvteluguindia"),
            YoutubeChannel("Zee Bangla", "@zeebangla"),
            YoutubeChannel("Mazhavil Manorama", "@mazhavilmanorama")
        ),
        "Pakistani News" to listOf(
            YoutubeChannel("Geo News", "@geonews"),
            YoutubeChannel("ARY News", "@ArynewsTvofficial"),
            YoutubeChannel("SAMAA TV", "@Samaatv"),
            YoutubeChannel("Dunya News", "@DunyanewsOfficial"),
            YoutubeChannel("BOL News", "@BOLNewsofficial"),
            YoutubeChannel("92 News HD", "@92newshdtv"),
            YoutubeChannel("GNN", "@gnnhdofficial"),
            YoutubeChannel("Public News", "@publicnewspk"),
            YoutubeChannel("DawnNews", "@dawnnewspakistan"),
            YoutubeChannel("Neo News", "@neonewshd"),
            YoutubeChannel("Lahore News HD", "@lahorenewsofficial"),
            YoutubeChannel("Express News", "@expressnewspkofficial"),
            YoutubeChannel("Capital TV", "@capitaltvlive"),
            YoutubeChannel("UrduPoint", "@UrduPointNetwork"),
            YoutubeChannel("Imran Riaz Khan", "@imranriazkhan1"),
            YoutubeChannel("Mansoor Ali Khan", "@mansooralikhanlive")
        ),
        "Indian News" to listOf(
            YoutubeChannel("Aaj Tak", "@aajtak"),
            YoutubeChannel("NMF News", "@nmfnews"),
            YoutubeChannel("DLS News", "@dlsnews"),
            YoutubeChannel("O News Hindi", "@onewshindi"),
            YoutubeChannel("TV9 iSmart News", "@ismartnewstv9"),
            YoutubeChannel("Prag News", "@pragnews-assam"),
            YoutubeChannel("DD News Mizoram", "@ddnewsaizawl"),
            YoutubeChannel("News Buzz", "@newsbuzzvideos")
        ),
        "Pakistani Music & Naats" to listOf(
            YoutubeChannel("Coke Studio Pakistan", "@cokestudio"),
            YoutubeChannel("KCH Multimedia Naat", "@KchMultimediaNaat"),
            YoutubeChannel("ARY QTV", "@ARYQtvofficial"),
            YoutubeChannel("Madani Channel", "@madanichannelofficial"),
            YoutubeChannel("Urdu Cover", "@uruducoverofficial"),
            YoutubeChannel("Ghulam Mustafa Qadri", "@ghulammustafaqadri"),
            YoutubeChannel("Islamic Releases", "@IslamicReleases"),
            YoutubeChannel("Allah Ki Qudrat", "@allahkiqudrat"),
            YoutubeChannel("Message TV", "@messagetvpk")
        ),
        "Indian Music & Bhakti" to listOf(
            YoutubeChannel("T-Series", "@tseries"),
            YoutubeChannel("T-Series Bhakti Sagar", "@tseriesbhaktisagar"),
            YoutubeChannel("Zee Music Company", "@zeemusiccompany"),
            YoutubeChannel("Sony Music India", "@sonymusicindia"),
            YoutubeChannel("Tips Official", "@tipsofficial"),
            YoutubeChannel("Saregama Music", "@saregamamusic"),
            YoutubeChannel("Ishtar Music", "@ishtarmusic"),
            YoutubeChannel("Sonotek", "@sonotek"),
            YoutubeChannel("Emiway Bantai", "@emiwaybantai"),
            YoutubeChannel("Pop Chartbusters", "@popchartbusters")
        ),
        "Hindi/Urdu Technology" to listOf(
            YoutubeChannel("Technical Guruji", "UCOhHO2ICt0ti9KAh-QHvttQ"),
            YoutubeChannel("Trakin Tech", "UCEPL07qzVsOcHd3sMUws65g"),
            YoutubeChannel("Technology Gyan", "UC1tVU8H153ZFO9eRsxdJlhA"),
            YoutubeChannel("Gyan Therapy", "UCS261lQDXjMvc8Jck-2CQEg"),
            YoutubeChannel("Techno Ruhez", "UC8vlDm5xvbxOrCvdu9UNOiA"),
            YoutubeChannel("Tech Burner", "UCXUJJNoP1QupwsYIWFXmsZg"),
            YoutubeChannel("Hasnain Technical", "UCxE248p2-PE6PK-AATg9mcg"),
            YoutubeChannel("Technical Guruji", "@technicalguruji"),
            YoutubeChannel("MR. INDIAN HACKER", "@mrindianhacker"),
            YoutubeChannel("FactTechz", "@facttechz"),
            YoutubeChannel("Technical Dost", "@technicaldost"),
            YoutubeChannel("Tech SuperStar", "@techsuperstarofficial"),
            YoutubeChannel("GFX Mentor", "@gfxmentor"),
            YoutubeChannel("vivo Pakistan", "@vivopakistan"),
            YoutubeChannel("PakWheels", "@PakWheels")
        ),
        "Hindi/Urdu Gaming" to listOf(
            YoutubeChannel("Techno Gamerz", "UCX8pnu3DYUnx8qy8V_c6oHg"),
            YoutubeChannel("Total Gaming", "UC5c9VlYTSvBSCaoMu_GI6gQ"),
            YoutubeChannel("CarryisLive", "UC0IWRLai-BAwci_e9MylNGw"),
            YoutubeChannel("Star Anonymous", "UCueAzFc4gCEJfAjQy1kcN3g"),
            YoutubeChannel("AS Gaming", "UCMrvxKTx9hLhZcOvJkYOnAw"),
            YoutubeChannel("Gyan Gaming", "UChXi_PlJkRMPYFQBOJ3MpxA"),
            YoutubeChannel("Lokesh Gamer", "UCdQPeeJ0qGK6wWBiEJWcdsQ"),
            YoutubeChannel("PUBG MOBILE Pakistan Official", "UCr5-mYyayqz7iQR3TzcoDng"),
            YoutubeChannel("Techno Gamerz", "@technogamerzofficial"),
            YoutubeChannel("Snax Gaming", "@snaxgaming"),
            YoutubeChannel("Gaming Subrata", "@gamingsubrata"),
            YoutubeChannel("Kronten Gaming", "@krontengaming"),
            YoutubeChannel("VAMPIRE YT", "@vampireyt1"),
            YoutubeChannel("PUBG MOBILE Pakistan Official", "@pubgmobilepakistan"),
            YoutubeChannel("Star ANONYMOUS", "@staranonymous")
        ),
        "Pakistani Vlogs" to listOf(
            YoutubeChannel("Ducky Bhai", "UC_c-RTowPbIlzMkIa_O7s6Q"),
            YoutubeChannel("Maaz Safder World", "UCz0M2pV4jOUUQ88lOd2q08A"),
            YoutubeChannel("Shahveer Jafry", "UChIrj5Qb5V-TZIRrk0Df-Uw"),
            YoutubeChannel("Zaid Ali T", "UCVBXum_ntfgHgHCOYtGOmEw"),
            YoutubeChannel("Rabeeca Khan", "UCfPL_q6AOLp0o8sP7t6FbXA"),
            YoutubeChannel("Brothers Vlog", "@itsbrothersvlog"),
            YoutubeChannel("Chota Ali Vlogs", "@chotaalivlogs"),
            YoutubeChannel("Anaya Eshaal Family", "@kashifnazir1419"),
            YoutubeChannel("Shaheer Jutt", "@shaheerjutt"),
            YoutubeChannel("Zulqarnain Sikandar", "@zulqarnainsikandar25"),
            YoutubeChannel("Rabeeca Khan", "@rabeecakhan"),
            YoutubeChannel("Hira Faisal", "@hirafaisal"),
            YoutubeChannel("Pak Town Vlogs", "@paktownvlog"),
            YoutubeChannel("Arslan Syed 1.0", "@arslansyed1.0"),
            YoutubeChannel("Hammad Shafique Vlogs", "@hammadshafiquevlogs"),
            YoutubeChannel("Javed Lifestyle", "@javedlifestyle786"),
            YoutubeChannel("IQRA kHAN VLOGS", "@iqrakhanfamily"),
            YoutubeChannel("Aon Vlogs", "@aonsheikh5")
        ),
        "Indian Vlogs & Lifestyle" to listOf(
            YoutubeChannel("Anaya Kandhal", "@anayakandhal"),
            YoutubeChannel("Aayu and Pihu Show", "@aayuandpihushow"),
            YoutubeChannel("Passenger Paramvir", "@passengerparamvir"),
            YoutubeChannel("Tanya Khanijow", "@tanyakhanijow"),
            YoutubeChannel("Niharika Nm", "@niharikanm"),
            YoutubeChannel("Anunay Sood", "@anunaysood"),
            YoutubeChannel("Kashvi Adlakha", "@kashviadlakha"),
            YoutubeChannel("BB Ki Vines", "@bbkivines"),
            YoutubeChannel("Kenny Sebastian", "@kennysebastian"),
            YoutubeChannel("KL BRO Biju Rithvik", "@klbrobijurithvik1"),
            YoutubeChannel("Flying Beast", "@flyingbeast"),
            YoutubeChannel("Sourav Joshi Vlogs", "@souravjoshivlogs")
        ),
        "Food & Cooking" to listOf(
            YoutubeChannel("Village Food Secrets", "@VillageFoodSecrets"),
            YoutubeChannel("Kitchen with Amna", "@KitchenWithAmna"),
            YoutubeChannel("Kun Foods", "@KunFoods"),
            YoutubeChannel("Kitchen With Mehnaz Abid", "@kitchenwithmehnazabid"),
            YoutubeChannel("Zia Tabarak", "@ziatabarak"),
            YoutubeChannel("Chef Neha Deepak Shah", "@nehadeepakshah"),
            YoutubeChannel("Preeti Samel Recipes", "@preetisamelrecipes"),
            YoutubeChannel("Homemade Recipe", "@homemaderecipe61"),
            YoutubeChannel("Recipes in My Way", "@recipesinmywaynonveg"),
            YoutubeChannel("Kabita's Kitchen", "@Kabitaskitchen"),
            YoutubeChannel("Nisha Madhulika", "@NishaMadhulika"),
            YoutubeChannel("Village Cooking Channel", "@VillageCookingChannel")
        ),
        "Education & Science" to listOf(
            YoutubeChannel("ilmkidunya", "@ilmkidunyaofficial"),
            YoutubeChannel("Qasim Ali Shah Official", "@qasimalishahofficial"),
            YoutubeChannel("Dear Sir", "@dearsir"),
            YoutubeChannel("MR. INDIAN HACKER", "@mrindianhacker"),
            YoutubeChannel("FactTechz", "@facttechz"),
            YoutubeChannel("Think School", "@ThinkSchool"),
            YoutubeChannel("GetsetflySCIENCE", "@GetsetflySCIENCE"),
            YoutubeChannel("Physics Wallah", "@PhysicsWallah"),
            YoutubeChannel("Khan GS Research Centre", "@KhanGSResearchCentre"),
            YoutubeChannel("Dekho Suno Jano", "@DekhoSunoJano"),
            YoutubeChannel("Sweet Voice", "@sweetvoice1122")
        ),
        "Islamic & Spiritual" to listOf(
            YoutubeChannel("Islamic Teacher Official", "@islamicteacherofficial786"),
            YoutubeChannel("Tariq Jamil", "@tariqjamilofficial"),
            YoutubeChannel("Madani Channel", "@madanichannelofficial"),
            YoutubeChannel("ARY QTV", "@ARYQtvofficial"),
            YoutubeChannel("Learn Quran at Home", "@learnquranathome"),
            YoutubeChannel("Qari Muhammad Ilyas", "@qarimuhammadilyas"),
            YoutubeChannel("Spread The Islam", "@spreadtheislam1"),
            YoutubeChannel("Asad Ali TV", "@asadalitv"),
            YoutubeChannel("Abdul Habib Attari", "@AbdulHabibAttari"),
            YoutubeChannel("T-Series Bhakti Sagar", "@tseriesbhaktisagar")
        ),
        "Kids & Family" to listOf(
            YoutubeChannel("Kids Land", "@kidslandofficial"),
            YoutubeChannel("Kids TV Urdu", "@kidstvurdunazamforkids"),
            YoutubeChannel("Fairy Tales Urdu", "@fairytalesurdu"),
            YoutubeChannel("Fun For Kids TV - Hindi Rhymes", "@funforkidstvhindirhymes"),
            YoutubeChannel("Aayu and Pihu Show", "@aayuandpihushow"),
            YoutubeChannel("ChuChu TV", "@ChuChuTV"),
            YoutubeChannel("Infobells", "@infobells")
        ),
        "Sports & Cricket" to listOf(
            YoutubeChannel("Pakistan Cricket", "@pakistancricket"),
            YoutubeChannel("Pakistan Super League", "@pakistansuperleagueofficial"),
            YoutubeChannel("Pakistani Cricket Reaction 2.0", "@pakistanicricketreaction2.0"),
            YoutubeChannel("Sports Tak", "@SportsTak"),
            YoutubeChannel("Star Sports", "@starsports"),
            YoutubeChannel("India Cricket", "@BCCI")
        ),
        "Documentary & History" to listOf(
            YoutubeChannel("Dekho Suno Jano", "@DekhoSunoJano"),
            YoutubeChannel("Haqeeqat TV", "@haqeeqattv"),
            YoutubeChannel("Sweet Voice", "@sweetvoice1122"),
            YoutubeChannel("ISPR Official", "@ispr"),
            YoutubeChannel("KHOJI TV", "@khojitv"),
            YoutubeChannel("That Was Crazy", "@ThatWasCrazy"),
            YoutubeChannel("VideoWaliSarkar", "@videowalisarkar1")
        ),
        "Travel & Outdoors" to listOf(
            YoutubeChannel("Zia Tabarak", "@ziatabarak"),
            YoutubeChannel("visa2explore", "@visa2explore"),
            YoutubeChannel("Passenger Paramvir", "@passengerparamvir"),
            YoutubeChannel("Anunay Sood", "@anunaysood"),
            YoutubeChannel("PakWheels", "@PakWheels"),
            YoutubeChannel("Village Food Secrets", "@VillageFoodSecrets")
        ),
        "Beauty, DIY & Home" to listOf(
            YoutubeChannel("Dilkash Beauty", "@dilkashbeauty1"),
            YoutubeChannel("Hassan Mehndi Expert", "@hassanmehndiexpert"),
            YoutubeChannel("Lillys Natural Tips", "@lillysnaturaltips4901"),
            YoutubeChannel("Aanam C", "@aanamc"),
            YoutubeChannel("Shabbir Edits", "@shabbiredits5")
        ),
        "Comedy & Podcasts" to listOf(
            YoutubeChannel("Mazaq Raat Show Official", "@mazaqraatshow"),
            YoutubeChannel("ARY Podcast", "@arypodcast"),
            YoutubeChannel("BB Ki Vines", "@bbkivines"),
            YoutubeChannel("Kenny Sebastian", "@kennysebastian"),
            YoutubeChannel("Aayu and Pihu Show", "@aayuandpihushow"),
            YoutubeChannel("Crazy Prank TV", "@CrazyPrankTV"),
            YoutubeChannel("Dumb TV", "@thedumbtv")
        ),
        "Business & Finance" to listOf(
            YoutubeChannel("Allied Bank", "@alliedbankltd"),
            YoutubeChannel("HBA Services", "@hbaservices"),
            YoutubeChannel("Mustafa Hanif", "@mustafahanif"),
            YoutubeChannel("Allied Bank", "@alliedbankltd")
        ),
        "Cars & Vehicles" to listOf(
            YoutubeChannel("PakWheels", "@PakWheels"),
            YoutubeChannel("RAJU CYCLE MECHANIC", "@rajucyclemechanic"),
            YoutubeChannel("vivo Pakistan", "@vivopakistan")
        )
    )

    val categoryNames: List<String>
        get() = categories.keys.toList()

    fun channelsFor(category: String): List<YoutubeChannel> =
        categories[category].orEmpty()

    val uniqueChannelCount: Int
        get() = categories.values.flatten().distinctBy { it.channelId }.size
}
