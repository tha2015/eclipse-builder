package sample.core

import java.io.File;
import org.apache.commons.io.FilenameUtils

class JdkDownloader {

    public File installJDK(File dir, String url, String src) {
        File jdkDir = new File(dir, FilenameUtils.getBaseName(url))

        if (!jdkDir.exists()) {

            // https://techtavern.wordpress.com/2014/03/25/portable-java-8-sdk-on-windows/
            unpackJDK(dir, downloadJDK(dir, url), jdkDir)

            if (src != null) {
                downloadWithOptionalMd5Check(new File(jdkDir, "src.zip"), src, null)
            } else if (!new File(jdkDir, "src.zip").exists()) {
                File srcfilePath = downloadJDK(dir, url.replace("windows", "linux").replace(".exe", ".tar.gz"))
                unpackJDKSrc(dir, srcfilePath, jdkDir)
            }
        }
        return jdkDir
    }
    private unpackJDKSrc(dir, srcfilePath, jdkDir) {
        def ant = new AntBuilder()
        ant.untar(src: srcfilePath, dest: jdkDir, compression:"gzip") {
            patternset {
                include(name: "**/src.zip")
            }
            mapper(type: "flatten")
        }
    }

    private File downloadJDK(File dir, String url) {
        String fileName = url.substring( url.lastIndexOf('/')+1, url.length() )
        File filePath = new File(dir, fileName)

        if (!filePath.exists()) {
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
                def file = filePath.newOutputStream()
                file << lastConn.inputStream
                file.close()
            }
        }
        return filePath
    }

    private File unpackJDK(File tmpDir, File jdkFilePath, File jdkDir) {
        // 7za920.zip MD5 2fac454a90ae96021f4ffc607d4c00f8
        // 7z920.msi MD5 9bd44a22bffe0e4e0b71b8b4cf3a80e2
        File file7za = new File(tmpDir, "7za.zip")
        File file7zmsi = new File(tmpDir, "7z.msi")
        File dir7zmsi = new File(tmpDir, "7zmsi")
        downloadWithOptionalMd5Check(file7za, "http://www.mirrorservice.org/sites/downloads.sourceforge.net/s/se/sevenzip/7-Zip/9.20/7za920.zip", "2fac454a90ae96021f4ffc607d4c00f8")
        downloadWithOptionalMd5Check(file7zmsi, "http://www.mirrorservice.org/sites/downloads.sourceforge.net/s/se/sevenzip/7-Zip/9.20/7z920.msi", "9bd44a22bffe0e4e0b71b8b4cf3a80e2")
        File file7zexe = unzip7z(tmpDir, file7za, file7zmsi, dir7zmsi)
        unzipjdk(tmpDir, file7zexe, jdkFilePath, jdkDir)
    }

    private String downloadWithOptionalMd5Check(File aFile, String url, String md5) {
        if (!aFile.exists()) {
            def ant = new AntBuilder()
            ant.get(src: url, dest: aFile)
            if (md5 != null) {
                ant.checksum(file: aFile.path, property: "checksum")
                String filemd5 = ant.project.properties.checksum
                if (!filemd5.equals(md5)) {
                    println 'not equal'
                    ant.delete(file: aFile)
                }
            }
        }
    }

    private File unzip7z(File tmpDir,File file7za, File file7zmsi, File dir7zmsi) {
        if (!dir7zmsi.exists()) {
            def ant = new AntBuilder()
            ant.unzip(src: file7za, dest: new File(tmpDir, '7za'))
            ant.exec(executable: new File(tmpDir, '7za/7za.exe'), dir: new File(tmpDir, '7za')) {
                arg (line: "e -y")
                arg (value: file7zmsi)
            }
            ant.move(todir: dir7zmsi) {
                fileset(dir: new File(tmpDir, '7za')) {
                  include(name:"_*.*")
                }
                mapper (type:"glob", from:"_*", to:"*")
            }
        }
        return new File(dir7zmsi, '7z.exe')
    }

    private void unzipjdk(File tmpDir, File file7zexe, File jdkFilePath, File jdkDir) {
        def ant = new AntBuilder()
        ant.mkdir (dir: jdkDir)
        ant.exec(executable: file7zexe) {
            arg (line: "e -y")
            arg (value: "-o${jdkDir}")
            arg (value: jdkFilePath)
        }
        ant.unzip(src: new File(jdkDir, 'tools.zip'), dest: jdkDir)
        ant.delete (file: new File(jdkDir, 'tools.zip'))
        jdkDir.eachDirRecurse() { dir ->
            dir.eachFileMatch(~/.*.pack/) { file ->
                String[] tokens = file.name.split("\\.(?=[^\\.]+\$)")

                ant.exec(executable: new File(jdkDir, 'bin/unpack200.exe'), dir: file.parentFile) {
                    arg (value: "--remove-pack-file")
                    arg (value: file)
                    arg (value: tokens[0] + ".jar")
                }
            }
        }
    }
}
