package sample.core;

import java.security.MessageDigest;
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.Comparator
import java.util.List;

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.FalseFileFilter
import org.apache.commons.io.filefilter.NameFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;


class EclipseDropInsBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(EclipseDropInsBuilder.class);
	private AntBuilder ant = new AntBuilder()


    public void build(Eclipse config, String xml) throws Exception {

        def workDir = new File(config.workDir)
        ant.mkdir (dir: workDir)

        def platformUrl = config.url
        def profile = config.profile
		String javaExeFile =  config.javaDir

		File installedContainerDir = new File(workDir, "installedContainerDir")
        ant.delete (dir: installedContainerDir)
		File cacheDir = new File(workDir, "cacheDir")
		File downloadedJdkDir = null
		Map<Plugin, File> cachedPlugins = new Hashtable<Plugin, File>();

		File downloadedContainerDir = downloadAndUnzip(workDir, platformUrl, cacheDir)


        ant.copy(todir: installedContainerDir) {fileset(dir: downloadedContainerDir)}
		File eclipseDir = findEclipseBaseDir(installedContainerDir)

        if (config.jdkUrl != null) {
            downloadedJdkDir = new JdkDownloader().installJDK(workDir, config.jdkUrl, config.jdkSrcUrl)
        }

		if (downloadedJdkDir != null) {
			ant.copy(todir: new File(eclipseDir, "jre")) {fileset(dir: downloadedJdkDir)}
		}

		if (javaExeFile == null && downloadedJdkDir != null) {
			javaExeFile = new File(downloadedJdkDir, "bin/javaw.exe").absolutePath
		}


		// 2. Build full Eclipse from configuration info and cache all plugins to cacheDir
		buildEclipseFromConfig(workDir, platformUrl, config.plugins, cachedPlugins, cacheDir, eclipseDir, javaExeFile, profile)

		// 3. Rebuild Eclipse again from cacheDir (but this time use dropins folder to keep plugins)
//		rebuildFullEclipseFromCache(installedContainerDir, eclipseDir, downloadedContainerDir, downloadedJdkDir, config.plugins, cachedPlugins)


        // 4. Increase memory settings
//		increaseEclipseMemory(installedContainerDir)

        // 5. Remove conflicting key binding from Aptana plugin (if any)
		removeAptanaKeybindings(eclipseDir, workDir)

        // 6. set execution permission for eclipse binaries
        ant.chmod(perm:"uog+x") { fileset(dir:installedContainerDir, includes:"**/eclipse, **/eclipse.exe, **/eclipsec.exe, **/eclipsec")}

		// 7. save profile xml content
		new File(getEclipsePluginsDir(eclipseDir).getParent(), "builConfig.xml").write(xml)

    }

	File downloadAndUnzip(File workDir, String platformUrl, File cacheDir) {
		File cachedEclipse

		MessageDigest md = MessageDigest.getInstance("SHA");
		md.update(platformUrl.getBytes("UTF-8"))
		String id = Hex.encodeHexString(md.clone().digest())
		id = "Eclipse_" + id.substring(0, 5)
		cachedEclipse = new File(cacheDir, id);
		if (!cachedEclipse.exists()) {
			def zipFileName = platformUrl.substring(platformUrl.lastIndexOf('/') + 1)
			def zipFileNameNoExt = zipFileName + "_unzipped"
			def unzippedDir = new File(workDir, zipFileNameNoExt)
			def unzippedEclipseHome = null
			if (!unzippedDir.exists()) {
				ant.get (src: platformUrl, dest: workDir, usetimestamp: false, skipexisting: true, verbose: true)
				try {

					FileInputStream fin = new FileInputStream(new File(workDir, zipFileName))
					byte[] bytes = new byte[2]
					fin.read(bytes)
					fin.close()

					if (bytes[0] == 0x50 && bytes[1] == 0x4b) {
						// 'PK' : zip
						ant.unzip (dest: new File(workDir, zipFileNameNoExt), overwrite:"false") { fileset(dir: workDir){ include (name: zipFileName) } }
					} else {
						ant.untar(dest:new File(workDir, zipFileNameNoExt), compression:"gzip", overwrite:"false") { fileset(dir: workDir){ include (name: zipFileName) } }
					}
				} catch (Exception e) {
					ant.delete(file: new File(workDir, zipFileName))
					throw e;
				}
			}
			ant.copy(todir: cachedEclipse) {fileset(dir: unzippedDir)}
		}

		return cachedEclipse
	}

	void buildEclipseFromConfig(File workDir, String platformUrl, List<Plugin> plugins, Map<Plugin, File> cachedPlugins, File cachedDir, File eclipseDir, String javaExeFile, String profile) {
		File snapshotDir = new File(workDir, "snapshotDir")
		ant.delete (dir: snapshotDir)

		MessageDigest md = MessageDigest.getInstance("SHA");
		md.update(platformUrl.getBytes("UTF-8"))
		for (Plugin plugin : plugins) {
			// try the cache first
			List<String> values = new ArrayList<String>();
			values.add(plugin.url)
			for (String s : plugin.updateSites) values.add(s)
			for (String s : plugin.featureIds) values.add(s)
			for (String s : values) {
				if (s != null) md.update(s.getBytes("UTF-8"))
			}
			String id = Hex.encodeHexString(md.clone().digest())
			id = plugin.name + "_" + id.substring(0, 5)
			File cachedPlugin = new File(cachedDir, id);
			println "Install plugin ${plugin.name} into ${id}"
			cachedPlugins.put(plugin, cachedPlugin)
			if (cachedPlugin.exists()) {
				// 1. create a snapshot
				ant.copy(todir: snapshotDir) {fileset(dir: eclipseDir)}
				// find cached files for plugin, use them
				ant.copy(todir: eclipseDir, overwrite: true) {fileset(dir: cachedPlugin)}
			} else {
				// 1. create a snapshot
				ant.copy(todir: snapshotDir) {fileset(dir: eclipseDir)}
				// 2. install
				if (plugin.url == 'http://downloads.zend.com/pdt/') {
					installPHPFromUrl(eclipseDir, workDir, ant, profile, plugin.url, plugin.featureIds)
				} else if (plugin.url != null) {
					installFromUrl(javaExeFile, eclipseDir, workDir, ant, profile, plugin.url, plugin.updateSites, plugin.featureIds)
				} else {
					installFromUpdateSite(javaExeFile, eclipseDir, ant, profile, plugin.updateSites, plugin.featureIds)
				}

//				// 3. compare with the snapshot and save new files to cachedPlugin folder
//				ant.copy(todir: new File(cachedPlugin, "features")) {
//					fileset(dir: getEclipseFeaturesDir(eclipseDir), includes: "**/*") {
//						present (present: "srconly", targetdir: new File(snapshotDir, "features"))
//					}
//				}
//				ant.copy(todir: new File(cachedPlugin, "plugins")) {
//					fileset(dir: getEclipsePluginsDir(eclipseDir), includes: "**/*") {
//						present (present: "srconly", targetdir: new File(snapshotDir, "plugins"))
//					}
//				}
//				ant.copy(todir: new File(cachedPlugin, "configuration")) {
//					fileset(dir: getEclipseConfigurationDir(eclipseDir), includes: "org.eclipse.equinox.simpleconfigurator/**, org.eclipse.equinox.source/**, org.eclipse.update/**, config.ini")
//				}
				// 4. test new jar/zip files broken or not
//				try {
//					ant.delete (dir: new File(workDir, "unzipped"))
//					ant.unzip(dest: new File(workDir, "unzipped")) {
//						fileset(dir: cachedPlugin, includes: "**/*.jar **/*.zip")
//					}
//				} catch (Exception e) {
//					ant.delete (dir: cachedPlugin)
//					throw e;
//				}
			}
		}
	}

//	void rebuildFullEclipseFromCache(File installedContainerDir, File eclipseDir, File downloadedDir, File downloadedJdkDir, List<Plugin> plugins, Map<Plugin, File> cachedPlugins) {
//		ant.delete (dir: installedContainerDir)
//		ant.copy(todir: installedContainerDir) {fileset(dir: downloadedDir)}
//
//		if (downloadedJdkDir != null) {
//			ant.copy(todir: new File(eclipseDir, "jre")) {fileset(dir: downloadedJdkDir)}
//		}
//
//		for (Plugin plugin : plugins) {
//			File cachedPlugin = cachedPlugins.get(plugin);
//			if (!plugin.isEmbeded()) {
//				ant.copy(todir: new File(getEclipseDropinsDir(eclipseDir), plugin.name)) {fileset(dir: cachedPlugin, excludes: "configuration/**")}
//			} else {
//				ant.copy(todir: getEclipsePluginsDir(eclipseDir).getParent()) {fileset(dir: cachedPlugin, excludes: "configuration/**")}
//			}
//			ant.copy(todir: getEclipsePluginsDir(eclipseDir).getParent(), overwrite: true) {fileset(dir: cachedPlugin, includes: "configuration/**/config.ini configuration/**/bundles.info configuration/**/source.info configuration/**/platform.xml")}
//		}
//	}

	void increaseEclipseMemory(def installeDir) {
		ant.replaceregexp (match:"^\\-Xmx[0-9]+m", replace:"-Xmx800m", byline:"true"){ fileset(dir:installeDir, includes:"**/eclipse.ini") }
		ant.replaceregexp (match:"^[0-9]+m", replace:"400m", byline:"true") { fileset(dir:installeDir, includes:"**/eclipse.ini") }
		ant.replaceregexp (match:"^[0-9]+M", replace:"400M", byline:"true") { fileset(dir:installeDir, includes:"**/eclipse.ini") }
	}

	void removeAptanaKeybindings(def eclipseDir, def workDir) {
		def files = FileUtils.listFiles(getEclipseDropinsDir(eclipseDir), new WildcardFileFilter("com.aptana.editor.common_*.jar"), TrueFileFilter.INSTANCE);
		if (!files.isEmpty()) {
			ant.delete(file: new File(workDir, "plugin.xml"))
			ant.unzip (src: files.get(0), dest: workDir){
				patternset {include (name:"plugin.xml")}
			}
			ant.replaceregexp (file: new File(workDir, "plugin.xml"),  match:"<key[^<]+CTRL\\+SHIFT\\+R[^<]+</key>", replace:"", flags:"s");
			ant.jar(destfile:files.get(0), basedir:workDir,includes:"plugin.xml",update:true)
		}
		files = FileUtils.listFiles(getEclipseDropinsDir(eclipseDir), new WildcardFileFilter("com.aptana.syncing.ui_*.jar"), TrueFileFilter.INSTANCE);
		if (!files.isEmpty()) {
			ant.delete(file: new File(workDir, "plugin.xml"))
			ant.unzip (src: files.get(0), dest: workDir){
				patternset {include (name:"plugin.xml")}
			}
			ant.replaceregexp (file: new File(workDir, "plugin.xml"),  match:"<key[^<]+M1\\+M2\\+U[^<]+</key>", replace:"", flags:"s");
			ant.jar(destfile:files.get(0), basedir:workDir,includes:"plugin.xml",update:true)
		}
	}

    void installFromUpdateSite(javaDir, eclipseDir, ant, profile, updateSites, featureIds) {
        def isWindows = (System.getProperty("os.name").indexOf("Windows") != -1);
        def javaPath = System.getProperty("java.home") + "/bin/java" + (isWindows ? ".exe" : "")
        if (javaDir != null && !javaDir.equals("")) javaPath = javaDir

        def directorCmd = new CommandLine(javaPath)

        def launcherPath = FileUtils.listFiles(getEclipsePluginsDir(eclipseDir), new WildcardFileFilter("org.eclipse.equinox.launcher_*.jar"), FalseFileFilter.FALSE).get(0).absolutePath
        directorCmd.addArgument("-jar").addArgument(launcherPath)
        directorCmd.addArgument("-application").addArgument("org.eclipse.equinox.p2.director")
        directorCmd.addArgument("-clean")
        directorCmd.addArgument("-profile").addArgument(profile)
        for (String updateSite : updateSites) {
            directorCmd.addArgument("-repository").addArgument(updateSite)
        }

        for (String featureId : featureIds) {
            ant.echo (message: "Will install " + featureId);
            directorCmd.addArgument("-installIU").addArgument(featureId)
        }
        directorCmd.addArgument("-destination")
        directorCmd.addArgument("\"" + eclipseDir.absolutePath + "\"")
        directorCmd.addArgument("-consoleLog")

        // Must be last args
        directorCmd.addArgument("-vmargs")
        directorCmd.addArgument("-Declipse.p2.mirrors=false")

        def executor = new DefaultExecutor();
        executor.setExitValue(0);
        println directorCmd
        def exitValue = executor.execute(directorCmd);

    }

    void installFromUrl(javaDir, File eclipseDir, workDir, ant, profile, url, updateSites, featureIds) {
        def fileName = url.substring(url.lastIndexOf('/') + 1)
        def downloadedFile = new File(workDir, fileName);
        if (!downloadedFile.exists()) {
            ant.get (src: url, dest: downloadedFile, usetimestamp: true, verbose: true)
        }
        List<String> names = new ArrayList<String>();
        ZipFile zf;
        try {
            zf = new ZipFile(downloadedFile);
            for (Enumeration entries = zf.entries(); entries.hasMoreElements();) {
                String zipEntryName = ((ZipEntry)entries.nextElement()).getName();
                names.add(zipEntryName);
            }
        } catch (Exception e) {
            ant.delete(file: downloadedFile);
            throw e;
        }
        println "zip file content: " + names
        if (names.contains("plugin.xml") || names.contains("META-INF/")) {
            // is simple jar contains plugin
            ant.copy (file: downloadedFile, todir: getEclipsePluginsDir(eclipseDir))
        } else if (names.contains("site.xml") || names.contains("content.jar") || names.contains("artifacts.jar")) {
            // is archive update site
            def updateSites2 = new ArrayList<String>();
            updateSites2.add("jar:" + downloadedFile.toURI().toURL().toString() + "!/")
            if (updateSites != null) updateSites2.addAll(updateSites)
            installFromUpdateSite(javaDir, eclipseDir, ant, profile, updateSites2, featureIds)
        } else {
            // is zipped plugins
            def tempDir = new File(workDir, downloadedFile.name + new Date().getTime())
            ant.unzip (src: downloadedFile, dest: tempDir)
            try {
                if (names.contains("eclipse/") || names.contains("plugins/")) {
                    ant.copy(todir: getEclipsePluginsDir(eclipseDir).getParent()){
                        fileset(dir: names.contains("eclipse/") ? new File(tempDir, "eclipse") : tempDir)
                    }
                } else {
                    Collection files = FileUtils.listFiles(tempDir, new NameFileFilter("plugin.xml"), TrueFileFilter.INSTANCE);
                    if (!files.isEmpty()) {
                        def fileList = []
                        fileList.addAll(files);
                        Collections.sort(fileList, new Comparator<File>(){
                                    public int compare(File f1, File f2) {
                                        return f1.getAbsolutePath().length() - f2.getAbsolutePath().length();
                                    }

                                });
                        File file = fileList.iterator().next();
                        if (file.getParentFile().getParentFile().getName().equals("plugins")) {
                            ant.copy(todir: getEclipsePluginsDir(eclipseDir).getParent()){
                                fileset(dir: file.getParentFile().getParentFile().getParentFile())
                            }
                        } else {
                            ant.copy(todir: getEclipsePluginsDir(eclipseDir)){
                                fileset(dir: file.getParentFile().getParentFile())
                            }
                        }
                    }
                }
            } finally {
                ant.delete(dir: tempDir)
            }
        }
    }
    void installPHPFromUrl(eclipseDir, workDir, ant, profile, url, featureIds) {

        def downloadedFile = new File(workDir, "org.zend.php.debug_feature_5.3.18.v20110322.jar");
        if (!downloadedFile.exists()) {
            ant.get (src: "http://downloads.zend.com/pdt/features/org.zend.php.debug_feature_5.3.18.v20110322.jar",
            dest: downloadedFile, usetimestamp: true, verbose: true)
        }
        ant.unzip (src: downloadedFile, dest: new File(getEclipseFeaturesDir(eclipseDir), downloadedFile.name))
        downloadedFile = new File(workDir, "org.zend.php.debug.debugger.win32.x86_5.3.18.v20110322.jar");
        if (!downloadedFile.exists()) {
            ant.get (src: "http://downloads.zend.com/pdt/plugins/org.zend.php.debug.debugger.win32.x86_5.3.18.v20110322.jar",
            dest: downloadedFile, usetimestamp: true, verbose: true)
        }
        ant.unzip (src: downloadedFile, dest: new File(getEclipsePluginsDir(eclipseDir), downloadedFile.name))
        downloadedFile = new File(workDir, "org.zend.php.debug.debugger_5.3.18.v20110322.jar");
        if (!downloadedFile.exists()) {
            ant.get (src: "http://downloads.zend.com/pdt/plugins/org.zend.php.debug.debugger_5.3.18.v20110322.jar",
            dest: downloadedFile, usetimestamp: true, verbose: true)
        }
        ant.unzip (src: downloadedFile, dest: new File(getEclipsePluginsDir(eclipseDir), downloadedFile.name))
    }

	File getEclipsePluginsDir(File eclipseDir) {
		if (new File(eclipseDir, "Contents/Eclipse/plugins").exists()) return new File(eclipseDir, "Contents/Eclipse/plugins")
		return new File(eclipseDir, "plugins")
	}

	File getEclipseFeaturesDir(File eclipseDir) {
		if (new File(eclipseDir, "Contents/Eclipse/features").exists()) return new File(eclipseDir, "Contents/Eclipse/features")
		return new File(eclipseDir, "features")
	}

	File getEclipseDropinsDir(File eclipseDir) {
		if (new File(eclipseDir, "Contents/Eclipse/dropins").exists()) return new File(eclipseDir, "Contents/Eclipse/dropins")
		return new File(eclipseDir, "dropins")
	}

	File getEclipseConfigurationDir(File eclipseDir) {
		if (new File(eclipseDir, "Contents/Eclipse/configuration").exists()) return new File(eclipseDir, "Contents/Eclipse/configuration")
		return new File(eclipseDir, 'configuration')
	}

	File findEclipseBaseDir(File installeEclipseDir) {
		if (new File(installeEclipseDir, "Eclipse.app/Contents/Eclipse").exists()) return new File(installeEclipseDir, "Eclipse.app")
		return new File(installeEclipseDir, 'eclipse')
	}


}
