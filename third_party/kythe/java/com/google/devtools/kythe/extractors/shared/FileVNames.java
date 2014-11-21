package com.google.devtools.kythe.extractors.shared;

import com.google.common.base.Preconditions;
import com.google.devtools.kythe.proto.Storage.VName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for configuring base {@link VName}s to use for particular file paths. Useful for
 * populating the {@link VName}s for each required input in a {@link CompilationUnit}.
 *
 * JSON format:
 *   <pre>[
 *     {
 *       "pattern": "pathRegex",
 *       "vname": {
 *         "corpus": "corpusTemplate",  // optional
 *         "root": "rootTemplate"       // optional
 *       }
 *     }, ...
 *   ]</pre>
 *
 * The {@link VName} template values can contain markers such as @1@ or @2@ that will be replaced by
 * the first or second regex groups in the pathRegex.
 *
 * NOTE: regex syntax is currently based on Java's
 *       http://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html spec.  This is likely
 *       to change.
 */
public class FileVNames {
  private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(Pattern.class, new PatternDeserializer())
      .create();
  private static final Type CONFIG_TYPE = new TypeToken<List<BaseFileVName>>() {}.getType();

  private final List<BaseFileVName> baseVNames;

  private FileVNames(List<BaseFileVName> baseVNames) {
    Preconditions.checkNotNull(baseVNames);
    for (BaseFileVName b : baseVNames) {
      Preconditions.checkNotNull(b.pattern, "pattern == null for base VName: " + b.vname);
      Preconditions.checkNotNull(b.vname, "vname template == null for pattern: " + b.pattern);
    }
    this.baseVNames = baseVNames;
  }

  /**
   * Returns a {@link FileVNames} that yields a static corpus-populated {@link VName} for each
   * {@link #lookupBaseVName(String)}.
   */
  public static FileVNames staticCorpus(String corpus) {
    return new FileVNames(Arrays.asList(
        new BaseFileVName(Pattern.compile(".*"), new VNameTemplate(corpus, null, null))));
  }

  public static FileVNames fromFile(String configFile) throws IOException {
    return new FileVNames(GSON.fromJson(new FileReader(configFile), CONFIG_TYPE));
  }

  public static FileVNames fromJson(String json) {
    return new FileVNames(GSON.fromJson(json, CONFIG_TYPE));
  }

  /**
   * Returns a base {@link VName} for the given file path. If none is configured, return
   * {@link VName#getDefaultInstance()}.
   */
  public VName lookupBaseVName(String path) {
    if (path != null) {
      for (BaseFileVName b : baseVNames) {
        Matcher matcher = b.pattern.matcher(path);
        if (matcher.matches()) {
          return b.vname.fillInWith(matcher);
        }
      }
    }
    return VName.getDefaultInstance();
  }

  /** Base {@link VName} to use for files matching {@code pattern}. */
  private static class BaseFileVName {
    // TODO(schroederc): ensure pattern syntax is consistent across implementations (probably by
    //                   using an RE2 implementation)
    public final Pattern pattern;
    public final VNameTemplate vname;

    public BaseFileVName(Pattern pattern, VNameTemplate vname) {
      this.pattern = pattern;
      this.vname = vname;
    }
  }

  /** Subset of a {@link VName} with built-in templating '@<num>@' markers. */
  private static class VNameTemplate {
    private final String corpus, root, path;

    public VNameTemplate(String corpus, String root, String path) {
      this.corpus = corpus;
      this.root = root;
      this.path = path;
    }

    /**
     * Returns a {@link VName} by filling in its corpus/root/path with regex groups in the given
     * {@link Matcher}.
     */
    public VName fillInWith(Matcher m) {
      VName.Builder b = VName.newBuilder();
      if (corpus != null) {
        b.setCorpus(fillIn(corpus, m));
      }
      if (root != null) {
        b.setRoot(fillIn(root, m));
      }
      if (path != null) {
        b.setPath(fillIn(path, m));
      }
      return b.build();
    }

    private static final Pattern replacerMatcher  = Pattern.compile("@(\\d+)@");
    private static String fillIn(String tmpl, Matcher m) {
      Matcher replacers = replacerMatcher.matcher(tmpl);
      Stack<MatchResult> matches = new Stack<MatchResult>();
      while (replacers.find()) {
        matches.push(replacers.toMatchResult());
      }
      StringBuilder builder = new StringBuilder(tmpl);
      while (!matches.isEmpty()) {
        MatchResult res = matches.pop();
        int grp = Integer.parseInt(res.group(1));
        builder.replace(res.start(), res.end(), m.group(grp));
      }
      return builder.toString();
    }

    @Override
    public String toString() {
      return String.format("{corpus: %s, root: %s, path: %s}", corpus, root, path);
    }
  }

  private static class PatternDeserializer implements JsonDeserializer<Pattern> {
    public Pattern deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return Pattern.compile(json.getAsJsonPrimitive().getAsString());
    }
  }
}
