package javaannbench.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class Config {

  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


  public static <T> T fromYaml(String fileName, Class<T> clazz) throws IOException {
    File yaml = new File("conf", fileName);
    try {
      return YAML_MAPPER.readValue(yaml, clazz);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public record BuildSpec(
          String dataset,
          String provider,
          String type,
          Map<String, String> build,
          Map<String, String> runtime) {

    public static BuildSpec load(String file) throws Exception {
      return Config.fromYaml(file, BuildSpec.class);
    }

    public String toString() {
      return String.format("%s_%s_%s_%s", dataset, provider, type, buildString());
    }

    public String buildString() {
      return build.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
              .collect(Collectors.joining("-"));
    }
  }

  public record QuerySpec(
          String dataset,
          String provider,
          String type,
          Map<String, String> build,
          Map<String, String> query,
          int k,

          // todo: optimize it
          Map<String, String> runtime) {

    public record RuntimeConfiguration(
            String systemMemory, String heapSize, int queryThreads, boolean jfr) {}

    public static QuerySpec load(String file) throws Exception {
      return Config.fromYaml(file, QuerySpec.class);
    }

    public String toString() {
      return String.format(
              "%s_%s_%s_%s_%s_%s_%s",
              dataset, provider, type, buildString(), queryString(), k, runtimeString());
    }

    public String buildString() {
      return build.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
              .collect(Collectors.joining("-"));
    }

    public String queryString() {
      return query.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
              .collect(Collectors.joining("-"));
    }

    public String runtimeString() {
      return runtime.entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
              .collect(Collectors.joining("-"));
    }
  }

}
