package sample.core;

import java.util.List;

public class Eclipse {
    private String workDir;
    private String javaDir;
    private String jdkUrl;
    private String jdkSrcUrl;
    private String dictionary;
    private String url;
    private String profile;
    private List<Plugin> plugins;

    public String getWorkDir() {
        return workDir;
    }
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }
    public String getJavaDir() {
        return javaDir;
    }
    public void setJavaDir(String javaDir) {
        this.javaDir = javaDir;
    }
    public String getJdkUrl() {
        return jdkUrl;
    }
    public void setJdkUrl(String jdkUrl) {
        this.jdkUrl = jdkUrl;
    }
    public String getJdkSrcUrl() {
        return jdkSrcUrl;
    }
    public void setJdkSrcUrl(String jdkSrcUrl) {
        this.jdkSrcUrl = jdkSrcUrl;
    }
    public String getDictionary() {
        return dictionary;
    }
    public void setDictionary(String dictionary) {
        this.dictionary = dictionary;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getProfile() {
        return profile;
    }
    public void setProfile(String profile) {
        this.profile = profile;
    }
    public List<Plugin> getPlugins() {
        return plugins;
    }
    public void setPlugins(List<Plugin> plugins) {
        this.plugins = plugins;
    }

}
