package org.zalando.intellij.swagger.file;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.util.LocalFileUrl;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class StoplightCreator {

  private static final String SWAGGER_UI_FOLDER_NAME = "stoplight";

  private final FileContentManipulator fileContentManipulator;

  public StoplightCreator(final FileContentManipulator fileContentManipulator) {
    this.fileContentManipulator = fileContentManipulator;
  }

  public Path createSwaggerUiFiles(final String specificationContent) throws Exception {
    Path cachePath =
        com.intellij.openapi.project.ProjectUtil.getProjectCachePath(
            Objects.requireNonNull(ProjectUtil.getActiveProject()), "stoplight-cache");
    copyFromJar(cachePath);
    setSwaggerConfigurationValues(new File(cachePath.toFile(), "index.html"), specificationContent);
    return cachePath;
  }

  public void updateSwaggerUiFile(
      final LocalFileUrl indexFileUrl, final String specificationContent) {
    final File indexFile = new File(Paths.get(indexFileUrl.getPath()).toUri());

    setSwaggerConfigurationValues(indexFile, specificationContent);
  }

  private void setSwaggerConfigurationValues(
      final File indexFile, final String specificationContent) {
    fileContentManipulator.setJsonToIndexFile(specificationContent, indexFile);
  }

  private void copyFromJar(final Path target) throws URISyntaxException, IOException {
    String resourcePath = "/" + SWAGGER_UI_FOLDER_NAME;
    URI resource = Objects.requireNonNull(getClass().getResource(resourcePath), "resource").toURI();
    try (FileSystem fileSystem = FileSystems.newFileSystem(resource, Map.of())) {

      final Path jarPath = fileSystem.getPath(resourcePath);

      try (Stream<Path> stream = Files.walk(jarPath)) {
        stream.forEachOrdered(
            path -> {
              try {
                if (Files.isDirectory(path)) {
                  final Path currentTarget = target.resolve(jarPath.relativize(path).toString());
                  Files.createDirectories(currentTarget);
                } else {
                  Files.copy(
                      path,
                      target.resolve(jarPath.relativize(path).toString()),
                      StandardCopyOption.REPLACE_EXISTING);
                }
              } catch (IOException e) {
                throw new UncheckedIOException("Failed to copy " + path, e);
              }
            });
      }
    }
  }
}
