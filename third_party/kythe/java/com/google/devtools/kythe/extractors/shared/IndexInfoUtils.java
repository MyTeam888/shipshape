package com.google.devtools.kythe.extractors.shared;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.devtools.kythe.common.PathUtil;
import com.google.devtools.kythe.proto.Analysis.CompilationUnit;
import com.google.devtools.kythe.proto.Analysis.FileData;
import com.google.protobuf.CodedInputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A class that contains utilities to read, write, and manipulate compilation index information.
 */
public class IndexInfoUtils {
  public static final String INDEX_FILE_EXT = ".kindex";

  public static CompilationDescription readIndexInfoFromFile(
      String indexInfoFilename) throws IOException {
    checkArgument(!Strings.isNullOrEmpty(indexInfoFilename), "indexInfoFilename");

    //TODO(jvg): switch to java.nio
    InputStream indexInfoInputStream = new GZIPInputStream(
        new FileInputStream(indexInfoFilename));
    try {
      return readIndexInfoFromStream(indexInfoInputStream);
    } finally {
      indexInfoInputStream.close();
    }
  }

  public static CompilationDescription readIndexInfoFromStream(
      InputStream inputStream) throws IOException {
    checkNotNull(inputStream, "inputStream");

    // We do not use parseDelimitedFrom but use CodedInputStream manually,
    // as it allows us to control the size limit on each individual protobuf message read.
    CodedInputStream codedStream = CodedInputStream.newInstance(inputStream);
    codedStream.setSizeLimit(Integer.MAX_VALUE);

    CompilationUnit compilationUnit = CompilationUnit.parseFrom(codedStream.readBytes());
    List<FileData> fileContents = Lists.newArrayList();
    while (!codedStream.isAtEnd()) {
      fileContents.add(FileData.parseFrom(codedStream.readBytes()));
    }
    return new CompilationDescription(compilationUnit, fileContents);
  }


  public static void writeIndexInfoToStream(CompilationDescription description,
      OutputStream stream) throws IOException {
    OutputStream indexOutputStream = new GZIPOutputStream(stream);
    try {
      description.getCompilationUnit().writeDelimitedTo(indexOutputStream);
      for (FileData fileContent : description.getFileContents()) {
        fileContent.writeDelimitedTo(indexOutputStream);
      }
    } finally {
      indexOutputStream.close();
    }
  }

  public static void writeIndexInfoToFile(CompilationDescription description, String path)
      throws IOException {
      writeIndexInfoToStream(description, new FileOutputStream(path));
  }

  public static String getIndexFilename(String rootDirectory, String basename) {
    return PathUtil.join(rootDirectory, basename + INDEX_FILE_EXT);
  }
}
