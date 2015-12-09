package sample.startup;

import java.io.File;
import java.io.OutputStream;
import java.util.logging.LogManager;
import java.util.zip.ZipException;

import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import sample.core.Eclipse;
import sample.core.EclipseDropInsBuilder;
import sample.core.Plugin;

import com.google.common.io.Files;
import com.thoughtworks.xstream.XStream;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    /**
     * MAIN ENTRY POINT.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        LogManager.getLogManager().reset(); // disable default java.util.logging.ConsoleHandler which logs to stdout
        SLF4JBridgeHandler.install(); // setup jul-to-slf4j to redirect all java.util.logging events to slf4j

        LOGGER.info("Launching application...");

        Resource config;
        if (args.length > 0) {
            if (new File(args[0]).exists()) {
                config = new FileSystemResource(args[0]);
            } else {
                config = new DefaultResourceLoader().getResource(args[0]);
            }
        } else {
            config = new ClassPathResource("/eclipse.xml");
        }
        XStream xstream = new XStream();
        xstream.alias("eclipse", Eclipse.class);
        xstream.alias("plugin", Plugin.class);
        xstream.addImplicitCollection(Plugin.class, "updateSites", "updateSite", String.class);
        xstream.addImplicitCollection(Plugin.class, "featureIds", "featureId", String.class);
        Eclipse profile = (Eclipse) xstream.fromXML(config.getInputStream());
        String dictionaryXml = (profile.getDictionary() != null) ? profile.getDictionary() : "/dictionary.xml";
        Eclipse dictionary = (Eclipse) xstream.fromXML(new ClassPathResource(dictionaryXml).getInputStream());
        int exitValue;
        do {
            exitValue = 0;
            try {
                EclipseDropInsBuilder builder = new EclipseDropInsBuilder();
                merge(profile, dictionary);
                builder.build(profile);
                OutputStream os = Files.newOutputStreamSupplier(new File(new File(profile.getWorkDir()),
                        "eclipse/builConfig.xml")).getOutput();
                xstream.toXML(profile, os);
                IOUtils.closeQuietly(os);
            } catch (ExecuteException ex) {
                LOGGER.debug("exception", ex);
                exitValue = ex.getExitValue();
            } catch (ZipException ex) {
                LOGGER.debug("exception", ex);
                exitValue = 14;
            }
        } while (exitValue == 13 || exitValue == 14);

        LOGGER.info("Exiting application...");
    }

    private static void merge(Eclipse profile, Eclipse dictionary) {
        // workDir javaDir dictionary url profile;
        if (profile.getWorkDir() == null) {
            profile.setWorkDir(dictionary.getWorkDir());
        }
        if (profile.getJavaDir() == null) {
            profile.setJavaDir(dictionary.getJavaDir());
        }
        if (profile.getDictionary() == null) {
            profile.setDictionary(dictionary.getDictionary());
        }
        if (profile.getUrl() == null) {
            profile.setUrl(dictionary.getUrl());
        }
        if (profile.getProfile() == null) {
            profile.setProfile(dictionary.getProfile());
        }

        for (Plugin plugin : profile.getPlugins()) {
            if (plugin.getUrl() == null && (plugin.getUpdateSites() == null || plugin.getUpdateSites().isEmpty())) {
                for (Plugin term : dictionary.getPlugins()) {
                    if (StringUtils.equals(plugin.getName(), term.getName())) {
                        plugin.setEmbeded(term.isEmbeded());
                        plugin.setFeatureIds(term.getFeatureIds());
                        plugin.setUpdateSites(term.getUpdateSites());
                        plugin.setUrl(term.getUrl());
                        break;
                    }
                }
            }
        }

    }
}
