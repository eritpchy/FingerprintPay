package com.surcumference.fingerprint.util;

import androidx.annotation.Keep;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipFile;

@Keep
public final class MemoryResourceLoader extends ClassLoader {
    private final String path;
    private final ZipFile resources;
    public MemoryResourceLoader(ClassLoader parent, String path) throws IOException {
        super(parent); this.path = path; resources = new ZipFile(path);
    }
    @Override protected java.util.Enumeration<URL> findResources(String name) {
        URL resource = findResource(name);
        return java.util.Collections.enumeration(resource == null ? java.util.Collections.<URL>emptyList() : java.util.Collections.singletonList(resource));
    }
    @Override protected URL findResource(String name) {
        if (name.startsWith("/") || name.contains("..") || resources.getEntry(name) == null) return null;
        try { return new URL("jar:file:" + path + "!/" + name); }
        catch (Exception ignored) { return null; }
    }
}
