package com.mobplayer.ytcrawler.model.youtube.format

import com.mobplayer.ytcrawler.Const

enum class Container(val ext: String) {
    MP4("mp4"),
    WEBM("webm"),
    M4A("m4a"),
    FLV("flv"),
    _3GP("3gp")
}

enum class AudioEncoding(val codec: String) {
    AAC("aac"),
    VORBIS("vorbis"),
    OPUS("opus"),
    MP3("mp3")
}

interface YouTubeFormat {
    val itag: String
    val container: Container
}

class UnknownFormat(
    override val itag: String,
    override val container: Container = Container.MP4
) : YouTubeFormat

class HlsManifest(
    override val itag: String = "hls",
    override val container: Container = Container.MP4
) : YouTubeFormat

enum class NonDash(
    override val itag: String,
    override val container: Container,
    val height: Int,
    val width: Int,
    val audioEncoding: AudioEncoding,
    val videoCodec: String,
    val audioBitrate: Int
) : YouTubeFormat {
    I5("5", Container.FLV, 240, 400, AudioEncoding.MP3, "h263", 64),
    I6("6", Container.FLV, 270, 450, AudioEncoding.MP3, "h263", 64),
    I13("13", Container._3GP, Const.UNKNOWN_VALUE, Const.UNKNOWN_VALUE, AudioEncoding.AAC, "mp4v", Const.UNKNOWN_VALUE),
    I17("17", Container._3GP, 144, 176, AudioEncoding.AAC, "mp4v", 24),
    I18("18", Container.MP4, 360, 640, AudioEncoding.AAC, "h264", 96),
    I22("22", Container.MP4, 720, 1280, AudioEncoding.AAC, "h264", 192),
    I34("34", Container.FLV, 360, 640, AudioEncoding.AAC, "h264", 128),
    I35("35", Container.FLV, 480, 854, AudioEncoding.AAC, "h264", 128),
    I36("36", Container._3GP, 240, 320, AudioEncoding.AAC, "mp4v", 32),
    I37("37", Container.MP4, 1080, 1920, AudioEncoding.AAC, "h264", 192),
    I38("38", Container.MP4, 3702, 4096, AudioEncoding.AAC, "h264", 192),
    I43("43", Container.WEBM, 360, 640, AudioEncoding.VORBIS, "vp8", 128),
    I44("44", Container.WEBM, 480, 854, AudioEncoding.VORBIS, "vp8", 128),
    I45("45", Container.WEBM, 720, 1280, AudioEncoding.VORBIS, "vp8", 192),
    I46("46", Container.WEBM, 1080, 1920, AudioEncoding.VORBIS, "vp8", 192),
    I59("59", Container.MP4, 480, 854, AudioEncoding.AAC, "h264", 128),
    I78("78", Container.MP4, 480, 854, AudioEncoding.AAC, "h264", 128)
}

enum class DashAudioOnly(
    override val itag: String,
    override val container: Container,
    val audioEncoding: AudioEncoding,
    val audioBitrate: Int
) : YouTubeFormat {
    I139("139", Container.M4A, AudioEncoding.AAC, 48),
    I140("140", Container.M4A, AudioEncoding.AAC, 128),
    I141("141", Container.M4A, AudioEncoding.AAC, 256),
    I256("256", Container.M4A, AudioEncoding.AAC, 128),
    I258("258", Container.M4A, AudioEncoding.AAC, 128),
    I171("171", Container.WEBM, AudioEncoding.VORBIS, 128),
    I172("172", Container.WEBM, AudioEncoding.VORBIS, 256),
    I249("249", Container.WEBM, AudioEncoding.OPUS, 50),
    I250("250", Container.WEBM, AudioEncoding.OPUS, 70),
    I251("251", Container.WEBM, AudioEncoding.OPUS, 160)
}

enum class DashVideoOnly(
    override val itag: String,
    override val container: Container,
    val codec: String,
    val height: Int,
    val width: Int = Const.UNKNOWN_VALUE
) : YouTubeFormat {
    I133("133", Container.MP4, "h264", 240),
    I134("134", Container.MP4, "h264", 360),
    I135("135", Container.MP4, "h264", 480),
    I136("136", Container.MP4, "h264", 720),
    I137("137", Container.MP4, "h264", 1080),
    I138("138", Container.MP4, "h264", 4320),
    I160("160", Container.MP4, "h264", 144),
    I212("212", Container.MP4, "h264", 480),
    I264("264", Container.MP4, "h264", 1440),
    I298("298", Container.MP4, "h264", 720),
    I299("299", Container.MP4, "h264", 1080),
    I266("266", Container.MP4, "h264", 2160),

    I167("167", Container.WEBM, "vp8", 360, 640),
    I168("168", Container.WEBM, "vp8", 480, 854),
    I169("169", Container.WEBM, "vp8", 720, 1280),
    I170("170", Container.WEBM, "vp8", 1080, 1920),

    I218("218", Container.WEBM, "vp8", 480, 854),
    I219("219", Container.WEBM, "vp8", 480, 854),

    I278("278", Container.WEBM, "vp9", 144),
    I242("242", Container.WEBM, "vp9", 240),
    I243("243", Container.WEBM, "vp9", 360),
    I244("244", Container.WEBM, "vp9", 480),
    I245("245", Container.WEBM, "vp9", 480),
    I246("246", Container.WEBM, "vp9", 480),
    I247("247", Container.WEBM, "vp9", 720),
    I248("248", Container.WEBM, "vp9", 1080),
    I271("271", Container.WEBM, "vp9", 1440),
    I272("272", Container.WEBM, "vp9", 4320),

    I302("302", Container.WEBM, "vp9", 720),
    I303("303", Container.WEBM, "vp9", 1080),
    I308("308", Container.WEBM, "vp9", 1440),
    I313("313", Container.WEBM, "vp9", 2160),
    I315("315", Container.WEBM, "vp9", 2160),

    I330("330", Container.WEBM, "vp9", 144),
    I331("331", Container.WEBM, "vp9", 240),
    I332("332", Container.WEBM, "vp9", 360),
    I333("333", Container.WEBM, "vp9", 480),
    I334("334", Container.WEBM, "vp9", 720),
    I335("335", Container.WEBM, "vp9", 1080)
}

enum class LiveStreaming(
    override val itag: String,
    override val container: Container,
    val videoResolution: Int,
    val audioEncoding: AudioEncoding,
    val audioBitrate: Int
) : YouTubeFormat {
    I91("91", Container.MP4, 144, AudioEncoding.AAC, 48),
    I92("92", Container.MP4, 240, AudioEncoding.AAC, 48),
    I93("93", Container.MP4, 360, AudioEncoding.AAC, 128),
    I94("94", Container.MP4, 480, AudioEncoding.AAC, 128),
    I95("95", Container.MP4, 720, AudioEncoding.AAC, 256),
    I96("96", Container.MP4, 1080, AudioEncoding.AAC, 256),
    I132("132", Container.MP4, 240, AudioEncoding.AAC, 48),
    I151("151", Container.MP4, 72, AudioEncoding.AAC, 24)
}

object FormatUtils {
    private val formatMap: Map<String, YouTubeFormat> by lazy {
        val map = HashMap<String, YouTubeFormat>()
        for (f in NonDash.values()) map[f.itag] = f
        for (f in DashAudioOnly.values()) map[f.itag] = f
        for (f in DashVideoOnly.values()) map[f.itag] = f
        for (f in LiveStreaming.values()) map[f.itag] = f
        map
    }

    @JvmStatic
    fun findByItag(itag: String): YouTubeFormat {
        return formatMap[itag] ?: UnknownFormat(itag)
    }
}
