package org.zalando.intellij.swagger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.zalando.intellij.swagger.file.FileContentManipulator;
import org.zalando.intellij.swagger.file.StoplightCreator;

public class SwaggerFileService {

  private final ConcurrentHashMap<String, Path> convertedSwaggerDocuments =
      new ConcurrentHashMap<>();
  private final StoplightCreator stoplightCreator =
      new StoplightCreator(new FileContentManipulator());
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private final JsonBuilderService jsonBuilderService = new JsonBuilderService();
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  private static final Logger log = Logger.getInstance(SwaggerFileService.class);

  public Optional<Path> convertSwaggerToHtml(@NotNull final VirtualFile virtualFile) {
    try {
      return convertSwaggerToHtmlWithoutCache(virtualFile, convertToJsonIfNecessary(virtualFile));
    } catch (Exception e) {
      notifyFailure(e);
      return Optional.empty();
    }
  }

  public void convertSwaggerToHtmlAsync(@NotNull final VirtualFile virtualFile) {
    executorService.submit(
        () ->
            ApplicationManager.getApplication()
                .runReadAction(
                    () -> {
                      try {
                        convertSwaggerToHtmlWithCache(
                            virtualFile, convertToJsonIfNecessary(virtualFile));
                      } catch (Exception e) {
                        // This is a no-op; we don't want to notify the user if the
                        // Swagger UI generation fails on file save. A user may
                        // edit a spec file that is invalid on multiple saves, and
                        // this would result in a large number of notifications.
                      }
                    }));
  }

  private void notifyFailure(final Exception exception) {
    log.info("Error generating Stoplight", exception);
    Notification notification =
        new Notification(
            "Stoplight",
            "Could not generate Stoplight",
            Objects.toString(exception),
            NotificationType.WARNING);

    Notifications.Bus.notify(notification);
  }

  private Optional<Path> convertSwaggerToHtmlWithoutCache(
      final VirtualFile virtualFile, final String contentAsJson) throws Exception {
    final Path path = stoplightCreator.createSwaggerUiFiles(contentAsJson);
    convertedSwaggerDocuments.put(virtualFile.getPath(), path);
    return Optional.of(path);
  }

  private void convertSwaggerToHtmlWithCache(final VirtualFile file, final String contentAsJson) {
    Optional.ofNullable(convertedSwaggerDocuments.get(file.getPath()))
        .ifPresent(
            dir -> {
              VirtualFile indexPath =
                  LocalFileSystem.getInstance().findFileByNioFile(dir.resolve("index.html"));
              stoplightCreator.updateSwaggerUiFile(indexPath, contentAsJson);
            });
  }

  private String convertToJsonIfNecessary(@NotNull final VirtualFile virtualFile) throws Exception {
    final JsonNode root = mapper.readTree(LoadTextUtil.loadText(virtualFile).toString());
    final JsonNode result = jsonBuilderService.buildFromSpec(root, virtualFile);

    return new ObjectMapper().writeValueAsString(result);
  }
}
