package org.rundeck.storage.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility methods for paths
 */
public class PathUtil {

    public static final Path ROOT = asPath("/");

    public static class PathImpl implements Path {
        String pathString;
        private String name;

        public PathImpl(String pathString) {
            this.pathString = pathString;
            name = pathName(pathString);
        }

        @Override
        public String getPath() {
            return pathString;
        }

        @Override
        public String toString() {
            return pathString;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathImpl)) return false;

            PathImpl path = (PathImpl) o;

            if (!pathString.equals(path.pathString)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return pathString.hashCode();
        }
    }

    public static Path asPath(String path) {
        if (null == path) {
            return null;
        }
        return new PathImpl(cleanPath(path));
    }

    /**
     * Return true if the given path starts with the given root
     *
     * @param path
     * @param root
     *
     * @return
     */
    public static boolean hasRoot(Path path, Path root) {
        return hasRoot(path.getPath(), root.getPath());
    }

    /**
     * Return true if the given path starts with the given root
     *
     * @param path
     * @param root
     *
     * @return
     */
    public static boolean hasRoot(String path, String root) {
        String p = cleanPath(path);
        String r = cleanPath(root);
        return p.equals(r) || p.startsWith(r + "/");
    }

    public static Path parentPath(Path path) {
        return asPath(parentPathString(path.getPath()));
    }

    /**
     * Return true if the path is the root
     *
     * @param path
     *
     * @return
     */
    public static boolean isRoot(String path) {
        return isRoot(asPath(path));
    }
    /**
     * Return true if the path is the root
     * @param path
     * @return
     */
    public static boolean isRoot(Path path) {
        return path.equals(ROOT);
    }

    /**
     * Return the string representing the parent of the given path
     * @param path path string
     * @return parent path string
     */
    public static String parentPathString(String path) {
        String[] split = cleanPath(path).split("/");
        if (split.length > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < split.length - 1; i++) {
                if (i > 0) {
                    stringBuilder.append("/");
                }
                stringBuilder.append(split[i]);
            }
            return stringBuilder.toString();
        }
        return "";
    }

    public static String cleanPath(String path) {
        if (path.endsWith("/")) {
            path = path.replaceAll("/+$", "");
        }
        if (path.startsWith("/")) {
            path = path.replaceAll("^/+", "");
        }
        return path.replaceAll("/+", "/");
    }

    public static String pathName(String path) {
        String[] split = cleanPath(path).split("/");
        if (split.length > 0) {
            return split[split.length - 1];
        }
        return null;
    }

    public static String removePrefix(String rootPath, String extpath) {
        if (!hasRoot(extpath, rootPath)) {
            return extpath;
        }
        return cleanPath(cleanPath(extpath).substring(cleanPath(rootPath).length()));
    }

    /**
     * Append one path to another
     *
     * @param prefix  prefix
     * @param subpath sub path
     *
     * @return sub path appended to the prefix
     */
    public static Path appendPath(Path prefix, String subpath) {
        return asPath(appendPath(prefix.getPath(), subpath));
    }

    /**
     * Append one path to another
     *
     * @param prefixPath prefix
     * @param subpath    sub path
     *
     * @return sub path appended to the prefix
     */
    public static String appendPath(String prefixPath, String subpath) {
        return cleanPath(prefixPath) + "/" + cleanPath(subpath);
    }

    /**
     * A Path selector that matches the given root path and any resource below it.
     *
     * @param rootPath
     *
     * @return
     */
    public static PathSelector subpathSelector(final Path rootPath) {
        return new PathSelector() {
            @Override
            public boolean matchesPath(Path path) {
                return path.equals(rootPath) || PathUtil.hasRoot(path, rootPath);
            }
        };
    }

    public static PathSelector allpathSelector() {
        return new PathSelector() {
            @Override
            public boolean matchesPath(Path path) {
                return true;
            }
        };
    }

    /**
     * A resource selector which requires metadata values to be equal to some required strings
     *
     * @param required   required metadata strings
     * @param requireAll if true, require all values are equal, otherwise require one value to be equal
     * @param <T>        content type
     *
     * @return selector for resources with all or some required metadata values
     */
    public static <T extends ContentMeta> ResourceSelector<T> exactMetadataResourceSelector(final Map<String,
            String> required, final boolean requireAll) {
        return new ResourceSelector<T>() {
            @Override
            public boolean matchesContent(T content) {
                for (String key : required.keySet()) {
                    String expect = required.get(key);
                    String test = content.getMeta().get(key);
                    if (null != test && expect.equals(test)) {
                        if (!requireAll) {
                            return true;
                        }
                    } else if (requireAll) {
                        return false;
                    }
                }
                return requireAll;
            }
        };
    }

    /**
     * A resource selector which requires metadata values to match regexes
     *
     * @param required   required metadata regexes
     * @param requireAll if true, require all values match regexes, otherwise require one value to match the regex
     * @param <T>        content type
     *
     * @return selector for resources with all or some matching metadata values
     */
    public static <T extends ContentMeta> ResourceSelector<T> regexMetadataResourceSelector(final Map<String,
            String> required, final boolean requireAll) {
        return new ResourceSelector<T>() {
            Map<String, Pattern> patternMap = new HashMap<String, Pattern>();

            private Pattern forString(String regex) {
                if (null == patternMap.get(regex)) {
                    Pattern compile = null;
                    try {
                        compile = Pattern.compile(regex);
                    } catch (PatternSyntaxException ignored) {
                        return null;
                    }
                    patternMap.put(regex, compile);
                }
                return patternMap.get(regex);
            }

            @Override
            public boolean matchesContent(T content) {
                for (String key : required.keySet()) {
                    Pattern pattern = forString(required.get(key));
                    String test = content.getMeta().get(key);
                    if (null != test && null != pattern && pattern.matcher(test).matches()) {
                        if (!requireAll) {
                            return true;
                        }
                    } else if (requireAll) {
                        return false;
                    }
                }
                return requireAll;
            }
        };
    }

    /**
     * compose two selectors
     *
     * @param a
     * @param b
     * @param and true indicates AND, otherwise OR
     * @param <T>
     *
     * @return new selector appyling the operator to the selector
     */
    public static <T extends ContentMeta> ResourceSelector<T> composeSelector(final ResourceSelector<T> a,
            final ResourceSelector<T> b, final boolean and) {
        return new ResourceSelector<T>() {
            @Override
            public boolean matchesContent(T content) {
                boolean a1 = a.matchesContent(content);
                if (a1 && !and || !a1 && and) {
                    return a1;
                }
                return b.matchesContent(content);
            }
        };
    }

    /**
     * A resource selector which always matches
     *
     * @param <T> content type
     *
     * @return selector
     */
    public static <T extends ContentMeta> ResourceSelector<T> allResourceSelector() {
        return new ResourceSelector<T>() {
            @Override
            public boolean matchesContent(T content) {
                return true;
            }
        };
    }


    /**
     * Lazy mechanism for stream loading
     *
     * @param data file
     *
     * @return
     */
    public static HasInputStream lazyFileStream(final File data) {
        return new HasInputStream() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(data);
            }
        };
    }

    /**
     * Lazy mechanism for stream loading
     *
     * @param data file
     *
     * @return
     */
    public static HasInputStream lazyStream(final InputStream data) {
        return new HasInputStream() {
            @Override
            public InputStream getInputStream() throws IOException {
                return data;
            }
        };
    }

    /**
     * Lazy mechanism for stream loading of existing content
     *
     * @param contentMeta
     *
     * @return
     */
    public static HasInputStream wrapStream(final ContentMeta contentMeta) {
        return new HasInputStream() {
            @Override
            public InputStream getInputStream() throws IOException {
                return contentMeta.readContent();
            }
        };

    }

    /**
     * Return a {@link ResourceSelector} constructed using this selector syntax:<br/>
     * <pre>
     * key OP value [; key OP value]*
     * </pre>
     * OP can be "=" (exact match) or "=~" (regular expression match).
     * <br/>
     * The returned selector effectively "AND"s the match requirements.
     * <br/>
     * The special string "*" equates to {@link #allResourceSelector()}
     *
     * @param selector the selector syntax string to parse, not null
     *
     * @return a resource selector corresponding to the parsed selector string
     */
    public static <T extends ContentMeta> ResourceSelector<T> resourceSelector(String selector) {
        if (null == selector) {
            throw new NullPointerException();
        }
        if("*".equals(selector)) {
            return allResourceSelector();
        }
        String[] split = selector.split(";");
        Map<String, String> values = new HashMap<String, String>();
        Map<String, String> regexes = new HashMap<String, String>();
        for (int i = 0; i < split.length; i++) {
            String s = split[i].trim();
            String[] split1 = s.split("=", 2);
            if (split1.length == 2) {
                String key = split1[0].trim();
                String value = split1[1];
                if (value.startsWith("~")) {
                    //regex
                    regexes.put(key, value.substring(1).trim());
                } else {
                    values.put(key, value.trim());
                }
            }
        }
        ResourceSelector<T> equalsSelector = null;
        ResourceSelector<T> regexSelector = null;

        if (values.size() > 0) {
            equalsSelector = PathUtil.exactMetadataResourceSelector(values, true);
        }
        if (regexes.size() > 0) {
            regexSelector = PathUtil.regexMetadataResourceSelector(regexes, true);
        }
        if (null == equalsSelector) {
            return regexSelector;
        }
        if (null == regexSelector) {
            return equalsSelector;
        }
        return PathUtil.composeSelector(equalsSelector, regexSelector, true);
    }
}
