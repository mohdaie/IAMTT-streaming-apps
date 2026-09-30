package com.iamtt.downloader;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;

public class ProtocolTest {
    @Test public void sourceFacetsRespectTokenBoundariesAndUnknowns() throws Exception {
        JSONObject s=new JSONObject().put("title","Film WEB-DL HDR10+ AAC ENGLISH");
        assertTrue(Protocol.sourceAttributes(s,"Release format").contains("WEB-DL"));
        assertTrue(Protocol.sourceAttributes(s,"Dynamic range").contains("HDR10+"));
        assertTrue(Protocol.sourceAttributes(s,"Audio").contains("AAC"));
        assertTrue(Protocol.sourceAttributes(s,"Language").contains("ENGLISH"));
        assertEquals("Unknown",Protocol.sourceAttributes(new JSONObject().put("title","CAMERON"),"Release format").get(0));
    }

    @Test public void preservesConfiguredAddonPath() {
        assertEquals("https://example.org/quality=1080p%7Ckey=abc",Protocol.base("stremio://example.org/quality=1080p%7Ckey=abc/manifest.json"));
    }
    @Test public void encodesSearchAndIdsAsOneSegment() {
        assertEquals("A%20%26%20B%2FC",Protocol.encode("A & B/C"));
        assertEquals("tt123%3A1%3A2",Protocol.encode("tt123:1:2"));
    }
    @Test public void rejectsWebPageAndCredentials() {
        for(String url:new String[]{"https://example.org/configure","http://example.org/manifest.json","https://user:pass@example.org/manifest.json","file:///manifest.json"}) {
            assertThrows(IllegalArgumentException.class,()->Protocol.manifestUrl(url));
        }
    }
    @Test public void checksTypesAndIdPrefixes() throws Exception {
        JSONObject m=new JSONObject("{\"types\":[\"movie\"],\"resources\":[{\"name\":\"stream\",\"types\":[\"movie\"],\"idPrefixes\":[\"tt\"]}]}");
        assertTrue(Protocol.resource(m,"stream","movie","tt123"));
        assertFalse(Protocol.resource(m,"stream","movie","other123"));
        assertFalse(Protocol.resource(m,"stream","series","tt123"));
    }
    @Test public void understandsBothSearchDeclarations() throws Exception {
        assertTrue(Protocol.searchable(new JSONObject("{\"extra\":[{\"name\":\"search\"}]}")));
        assertTrue(Protocol.searchable(new JSONObject("{\"extraSupported\":[\"search\"]}")));
        assertFalse(Protocol.browsable(new JSONObject("{\"extra\":[{\"name\":\"search\",\"isRequired\":true}]}")));
    }
    @Test public void selectsRequestedFileNotEntirePack() {
        assertEquals(0,Protocol.selectFile(0,new String[]{"movie.mp4","bigger.mkv"},new long[]{10,100}));
        assertThrows(IllegalArgumentException.class,()->Protocol.selectFile(4,new String[]{"movie.mp4"},new long[]{10}));
    }
    @Test public void choosesLargestVideoNotArchive() {
        assertEquals(1,Protocol.selectFile(-1,new String[]{"sample.mp4","film.MKV","extras.zip"},new long[]{10,100,1000}));
        assertThrows(IllegalArgumentException.class,()->Protocol.selectFile(-1,new String[]{"payload.exe"},new long[]{10}));
    }
    @Test public void parsesTorrentioStyleSourceMetadata() throws Exception {
        JSONObject stream=new JSONObject()
            .put("description","1080p HEVC\n👤 27 💾 1.42 GB ⚙️ YTS")
            .put("infoHash","0123456789012345678901234567890123456789");
        assertEquals("1080p",Protocol.sourceQuality(stream));
        assertEquals("HEVC",Protocol.sourceCodec(stream));
        assertEquals("YTS",Protocol.sourcePublisher(stream));
        assertEquals(27,Protocol.reportedSeeders(stream));
        assertTrue(Protocol.sourceSize(stream)>1400L*1024*1024);
        assertEquals("Strong",Protocol.sourceHealth(stream));
    }
    @Test public void prefersBehaviorHintVideoSize() throws Exception {
        JSONObject stream=new JSONObject().put("description","💾 99 GB")
            .put("behaviorHints",new JSONObject().put("videoSize",734003200));
        assertEquals(734003200L,Protocol.sourceSize(stream));
    }
    @Test public void readsYearsFromCommonCatalogueFields() throws Exception {
        assertEquals(2026,Protocol.year(new JSONObject("{\"releaseInfo\":\"2026–\"}")));
        assertEquals(2018,Protocol.year(new JSONObject("{\"year\":2018}")));
        assertEquals(2024,Protocol.year(new JSONObject("{\"released\":\"2024-04-01T00:00:00.000Z\"}")));
        assertEquals(0,Protocol.year(new JSONObject("{}")));
    }
    @Test public void treatsNullFileIndexAsUnspecified() throws Exception {
        assertEquals(-1,Protocol.fileIndex(new JSONObject("{\"fileIdx\":null}")));
        assertEquals(0,Protocol.fileIndex(new JSONObject("{\"fileIdx\":0}")));
        assertThrows(IllegalArgumentException.class,()->Protocol.fileIndex(new JSONObject("{\"fileIdx\":-1}")));
    }
    @Test public void buildsMagnetWithTrackerHints() throws Exception {
        JSONObject stream=new JSONObject().put("infoHash","0123456789012345678901234567890123456789")
            .put("sources",new JSONArray().put("tracker:udp://tracker.example:80/announce").put("dht:unused"));
        String magnet=Protocol.magnet(stream,"My film");
        assertTrue(magnet.contains("&dn=My%20film"));assertTrue(magnet.contains("&tr=udp%3A%2F%2Ftracker.example%3A80%2Fannounce"));
        assertFalse(magnet.contains("dht:"));
    }
    @Test public void preventsPathTraversal() throws Exception {
        File root=new File(System.getProperty("java.io.tmpdir"),"iamtt-test");
        assertEquals(new File(root,"movie/video.mp4").getCanonicalFile(),Protocol.safeFile(root,"movie/video.mp4"));
        assertThrows(IOException.class,()->Protocol.safeFile(root,"../../outside"));
        assertThrows(IOException.class,()->Protocol.safeFile(root,"/outside"));
    }
}
