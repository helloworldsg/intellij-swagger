package org.zalando.intellij.swagger.service;

public class SwaggerFilesUtils {

  public static boolean isFileReference(final String text) {
    return text.endsWith(".json")
        || text.contains(".json#/")
        || text.endsWith(".yaml")
        || text.contains(".yaml#/")
        || text.endsWith(".yml")
        || text.contains(".yml#/");
  }
}
