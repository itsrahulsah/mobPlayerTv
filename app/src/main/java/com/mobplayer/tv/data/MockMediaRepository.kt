package com.mobplayer.tv.data

import com.mobplayer.tv.data.models.CardType
import com.mobplayer.tv.data.models.MediaItemModel
import com.mobplayer.tv.data.models.MediaRailModel

object MockMediaRepository {

    val featuredItem = MediaItemModel(
        id = "featured_1",
        title = "CYBERPUNK 2099: REBELLION",
        subtitle = "A dystopian thriller",
        description = "In a neon-drenched metropolis controlled by rogue AI syndicates, a solitary hacker discovers a weaponized algorithm that could dismantle the digital empire or destroy the human mind.",
        posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80",
        backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1600&auto=format&fit=crop&q=80",
        genres = listOf("Sci-Fi", "Cyberpunk", "Action", "Thriller"),
        year = "2024",
        duration = "2h 18m",
        rating = "9.2",
        contentRating = "18+",
        badge = "FEATURED PREMIERE",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        gradientColors = listOf(0xFF2E0854, 0xFF0B001A)
    )

    private val continueWatchingList = listOf(
        MediaItemModel(
            id = "cw_1",
            title = "Cosmic Odyssey: Deep Space",
            subtitle = "Episode 4 • The Wormhole",
            description = "The crew of the Vanguard navigates uncharted gravitational anomalies beyond the Sagittarius Arm.",
            posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Sci-Fi", "Adventure"),
            year = "2024",
            duration = "52m",
            rating = "8.9",
            progress = 0.68f,
            remainingTime = "18m remaining",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            gradientColors = listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364)
        ),
        MediaItemModel(
            id = "cw_2",
            title = "Tokyo Midnight Drift",
            subtitle = "Underground Season 2",
            description = "High stakes street racing across the rain-slicked expressways of Shinjuku and Shibuya.",
            posterUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1542051841857-5f90071e7989?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Action", "Crime"),
            year = "2023",
            duration = "46m",
            rating = "8.6",
            progress = 0.42f,
            remainingTime = "26m remaining",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            gradientColors = listOf(0xFF8A2387, 0xFFE94057, 0xFFF27121)
        ),
        MediaItemModel(
            id = "cw_3",
            title = "The Nordic Mystery",
            subtitle = "Episode 7 • Frozen Secrets",
            description = "Detective Astrid unravels a series of unexplained disappearances along the Arctic coast.",
            posterUrl = "https://images.unsplash.com/photo-1517411032315-54ef2cb783bb?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Crime", "Drama", "Mystery"),
            year = "2024",
            duration = "58m",
            rating = "8.8",
            progress = 0.85f,
            remainingTime = "9m remaining",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            gradientColors = listOf(0xFF1F1C2C, 0xFF928DAB)
        ),
        MediaItemModel(
            id = "cw_4",
            title = "Apex Legends: Tournament",
            subtitle = "Grand Finals Game 5",
            description = "The world's top esports teams collide in the grand championship.",
            posterUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Esports", "Live Gaming"),
            year = "2024",
            duration = "1h 12m",
            rating = "9.1",
            progress = 0.30f,
            remainingTime = "50m remaining",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            gradientColors = listOf(0xFF141E30, 0xFF243B55)
        )
    )

    private val topMoviesList = listOf(
        MediaItemModel(
            id = "movie_1",
            title = "Chronicles of Nebula",
            description = "An ancient artifact discovered on Mars triggers an interstellar race for unimaginable quantum energy.",
            posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Sci-Fi", "Action"),
            year = "2024",
            duration = "2h 24m",
            rating = "9.0",
            rankNumber = 1,
            badge = "4K ULTRA HD",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            gradientColors = listOf(0xFF11998E, 0xFF38EF7D)
        ),
        MediaItemModel(
            id = "movie_2",
            title = "Shadows in the Deep",
            description = "Submarine researchers discover a subterranean prehistoric ecosystem beneath the Mariana Trench.",
            posterUrl = "https://images.unsplash.com/photo-1682687220063-4742bd7fd538?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Thriller", "Adventure"),
            year = "2024",
            duration = "1h 58m",
            rating = "8.7",
            rankNumber = 2,
            badge = "IMAX ENHANCED",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            gradientColors = listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364)
        ),
        MediaItemModel(
            id = "movie_3",
            title = "The Last Automaton",
            description = "In a world where mechanical beings have vanished, one remaining guardian protects humanity's final archive.",
            posterUrl = "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Sci-Fi", "Drama"),
            year = "2023",
            duration = "2h 05m",
            rating = "8.6",
            rankNumber = 3,
            badge = "HDR10+",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            gradientColors = listOf(0xFF3A6073, 0xFF3A7BD5)
        ),
        MediaItemModel(
            id = "movie_4",
            title = "Velocity Zero",
            description = "A daring getaway pilot is recruited for an impossible high-speed heist across five European capitals.",
            posterUrl = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1617814076367-b759c7d7e738?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Action", "Crime"),
            year = "2024",
            duration = "1h 48m",
            rating = "8.5",
            rankNumber = 4,
            badge = "DOLBY ATMOS",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4",
            gradientColors = listOf(0xFFEB3349, 0xFFF45C43)
        ),
        MediaItemModel(
            id = "movie_5",
            title = "The Alchemist's Legacy",
            description = "Secret societies clash over medieval alchemical manuscripts that unlock atomic transmutation.",
            posterUrl = "https://images.unsplash.com/photo-1532012164546-f432f2e3777a?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1519791883288-dc8bd696e667?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Fantasy", "Mystery"),
            year = "2023",
            duration = "2h 12m",
            rating = "8.4",
            rankNumber = 5,
            badge = "4K ULTRA HD",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4",
            gradientColors = listOf(0xFF4B1248, 0xFFF0C27B)
        ),
        MediaItemModel(
            id = "movie_6",
            title = "Solar Flare: Apocalypse",
            description = "A catastrophic solar storm disables the world's power grids, plunging civilisation into survival mode.",
            posterUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1470240731273-7821a6eeb6bd?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Disaster", "Thriller"),
            year = "2024",
            duration = "2h 10m",
            rating = "8.3",
            rankNumber = 6,
            badge = "HDR10+",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            gradientColors = listOf(0xFFF12711, 0xFFF5AF19)
        )
    )

    private val popularSeriesList = listOf(
        MediaItemModel(
            id = "series_1",
            title = "Silicon Syndicate",
            subtitle = "Season 4 • 10 Episodes",
            description = "Billionaire tech founders engage in cutthroat espionage to control artificial intelligence patents.",
            posterUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Drama", "Tech", "Thriller"),
            year = "2024",
            duration = "Season 4",
            rating = "9.1",
            badge = "NEW EPISODES",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            gradientColors = listOf(0xFF000428, 0xFF004E92)
        ),
        MediaItemModel(
            id = "series_2",
            title = "The Iron Vanguard",
            subtitle = "Season 3 • 12 Episodes",
            description = "Warring noble houses forge fragile alliances in a grand medieval fantasy empire.",
            posterUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Fantasy", "Action", "Drama"),
            year = "2024",
            duration = "Season 3",
            rating = "8.9",
            badge = "TOP RATED",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            gradientColors = listOf(0xFF330867, 0xFF30CFD0)
        ),
        MediaItemModel(
            id = "series_3",
            title = "Echoes of Eternity",
            subtitle = "Season 2 • 8 Episodes",
            description = "A team of theoretical physicists discover that alternate dimensions are bleeding into our reality.",
            posterUrl = "https://images.unsplash.com/photo-1507499739999-097706ad8914?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Sci-Fi", "Mystery"),
            year = "2023",
            duration = "Season 2",
            rating = "8.8",
            badge = "4K HDR",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            gradientColors = listOf(0xFF2C3E50, 0xFF3498DB)
        ),
        MediaItemModel(
            id = "series_4",
            title = "Undercover Sector 7",
            subtitle = "Season 1 • 6 Episodes",
            description = "An elite intelligence task force operates completely off the books to prevent global terror networks.",
            posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1542051841857-5f90071e7989?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Action", "Espionage"),
            year = "2024",
            duration = "Season 1",
            rating = "8.6",
            badge = "BINGE WORTHY",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
            gradientColors = listOf(0xFF434343, 0xFF000000)
        )
    )

    private val liveChannelsList = listOf(
        MediaItemModel(
            id = "live_1",
            title = "Red Bull Extreme Sports 4K",
            subtitle = "World Off-Road Championship",
            description = "High octane off-road rally racing live from the Mojave Desert.",
            posterUrl = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Sports", "Motorsport"),
            year = "LIVE",
            duration = "STREAMING",
            rating = "LIVE",
            isLive = true,
            channelNumber = "CH 101",
            badge = "LIVE 4K",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            gradientColors = listOf(0xFFB71C1C, 0xFF212121)
        ),
        MediaItemModel(
            id = "live_2",
            title = "Galaxy Cinema 24/7",
            subtitle = "Sci-Fi Classics Marathon",
            description = "Continuous round-the-clock streaming of science fiction and fantasy cinema masterpieces.",
            posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Cinema", "Sci-Fi"),
            year = "LIVE",
            duration = "STREAMING",
            rating = "LIVE",
            isLive = true,
            channelNumber = "CH 204",
            badge = "LIVE",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            gradientColors = listOf(0xFF1A237E, 0xFF0D47A1)
        ),
        MediaItemModel(
            id = "live_3",
            title = "Pulse Music Live",
            subtitle = "Ultra Electronic Music Festival",
            description = "Live DJ sets and electronic dance music sets from Amsterdam and Miami.",
            posterUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("Music", "Festival"),
            year = "LIVE",
            duration = "STREAMING",
            rating = "LIVE",
            isLive = true,
            channelNumber = "CH 310",
            badge = "LIVE",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            gradientColors = listOf(0xFF4A148C, 0xFF880E4F)
        ),
        MediaItemModel(
            id = "live_4",
            title = "Global Tech News 24",
            subtitle = "AI Breakthroughs Summit",
            description = "Live keynote speeches, technology launches and expert roundtable debates.",
            posterUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=1200&auto=format&fit=crop&q=80",
            genres = listOf("News", "Technology"),
            year = "LIVE",
            duration = "STREAMING",
            rating = "LIVE",
            isLive = true,
            channelNumber = "CH 405",
            badge = "LIVE",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            gradientColors = listOf(0xFF006064, 0xFF004D40)
        )
    )

    val contentRails = listOf(
        MediaRailModel(
            id = "rail_continue_watching",
            title = "Continue Watching",
            subtitle = "Pick up where you left off",
            cardType = CardType.CONTINUE_WATCHING,
            items = continueWatchingList
        ),
        MediaRailModel(
            id = "rail_top_movies",
            title = "Top 10 Movies Today",
            subtitle = "Most watched in your region",
            cardType = CardType.POSTER,
            items = topMoviesList
        ),
        MediaRailModel(
            id = "rail_popular_series",
            title = "Popular TV Shows & Series",
            subtitle = "Critically acclaimed drama & sci-fi",
            cardType = CardType.LANDSCAPE,
            items = popularSeriesList
        ),
        MediaRailModel(
            id = "rail_live_channels",
            title = "Live Channels & Sports",
            subtitle = "Broadcasting live right now",
            cardType = CardType.LIVE,
            items = liveChannelsList
        )
    )
}
