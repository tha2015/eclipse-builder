package sample.startup;

import static org.junit.Assert.*;

import org.junit.Test;

class TestDownloadJDK {

    @Test
    public void test() {

        String url = "http://download.oracle.com/otn-pub/java/jdk/8u40-b26/jdk-8u40-windows-x64.exe";

        HttpURLConnection lastConn = null
        for (int i = 0; i < 5 && url != null; i++) {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url).openConnection())
            conn.setRequestProperty("Cookie", "gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie")
            conn.connect()
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                lastConn = conn
                break
            }
            if (conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || conn.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                url = conn.getHeaderField("Location")
            } else {
                url = null;
            }
            conn.disconnect()
        }
        if (lastConn != null) {
            def file = new File("D:\\jdk-8u40-windows-x64.exe").newOutputStream()
            file << lastConn.inputStream
            file.close()
        }

        // unpack https://techtavern.wordpress.com/2014/03/25/portable-java-8-sdk-on-windows/
    }
}
