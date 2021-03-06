package com.google.api.codegen.bazel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

class BazelBuildFileView {
  private static final Pattern LABEL_NAME = Pattern.compile(":\\w+$");
  private final Map<String, String> tokens = new HashMap<>();

  BazelBuildFileView(ApiVersionedDir bp) {
    if (bp.getProtoPackage() == null) {
      return;
    }
    tokens.put("name", bp.getName());
    tokens.put("assembly_name", bp.getAssemblyName());

    tokens.put("proto_srcs", joinSetWithIndentation(bp.getProtos()));
    tokens.put("version", bp.getVersion());
    tokens.put("package", bp.getProtoPackage());

    String packPrefix = bp.getProtoPackage().replace(".", "/") + '/';
    Set<String> actualImports = new TreeSet<>();
    for (String imp : bp.getImports()) {
      if (imp.startsWith(packPrefix) && imp.indexOf('/', packPrefix.length()) == -1) {
        // Ignore imports from same package, as all protos in same package are put in same
        // proto_library target.
        continue;
      }

      String actualImport = imp.replace(".proto", "_proto");
      if (actualImport.startsWith("google/protobuf/")) {
        actualImport = actualImport.replace("google/protobuf/", "@com_google_protobuf//:");
      } else {
        actualImport = convertPathToLabel("", actualImport);
      }
      actualImports.add(actualImport);
    }
    tokens.put("proto_deps", joinSetWithIndentation(actualImports));
    tokens.put("go_proto_importpath", bp.getLangProtoPackages().get("go").split(";")[0]);
    tokens.put("go_proto_deps", joinSetWithIndentation(mapGoProtoDeps(actualImports)));

    if (bp.getGapicYamlPath() == null) {
      return;
    }

    tokens.put("gapic_yaml", convertPathToLabel(bp.getProtoPackage(), bp.getGapicYamlPath()));
    tokens.put("service_yaml", convertPathToLabel(bp.getProtoPackage(), bp.getServiceYamlPath()));

    Set<String> javaTests = new TreeSet<>();
    for (String service : bp.getServices()) {
      String javaPackage = bp.getLangGapicPackages().get("java");
      if (javaPackage == null) {
        continue;
      }

      String actualService =
          bp.getLangGapicNameOverrides()
              .get("java")
              .getOrDefault(bp.getProtoPackage() + "." + service, service);
      if (actualService.startsWith("IAM")) {
        actualService = actualService.replaceAll("^IAM", "Iam");
      }
      javaTests.add(javaPackage + "." + actualService + "ClientTest");
    }
    tokens.put("java_tests", joinSetWithIndentation(javaTests));
    tokens.put("java_gapic_deps", joinSetWithIndentationNl(mapJavaGapicDeps(actualImports)));
    tokens.put(
        "java_gapic_test_deps", joinSetWithIndentationNl(mapJavaGapicTestDeps(actualImports)));

    tokens.put("go_gapic_importpath", bp.getLangGapicPackages().get("go").split(";")[0]);
    tokens.put("go_gapic_deps", joinSetWithIndentationNl(mapGoGapicDeps(actualImports)));
  }

  private String convertPathToLabel(String pkg, String path) {
    if (path == null) {
      return path;
    }
    if (!path.contains("/")) {
      return path;
    }

    String[] pkgTokens = pkg.isEmpty() ? new String[0] : pkg.split("\\.");
    String[] pathTokens = path.split("/");

    // Find pkgTokens suffix & pathTokens prefix intersection
    int index = 0;
    for (; index < pkgTokens.length && index < pathTokens.length; index++) {
      if (!pathTokens[index].equals(pkgTokens[pkgTokens.length - index - 1])) {
        break;
      }
    }

    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < pkgTokens.length - index; i++) {
      tokens.add(pkgTokens[i]);
    }
    for (int i = index; i < pathTokens.length; i++) {
      tokens.add(pathTokens[i]);
    }

    StringBuilder sb = new StringBuilder("/");
    for (String token : tokens) {
      sb.append('/').append(token);
    }
    int lastSlashIndex = sb.lastIndexOf("/");
    sb.replace(lastSlashIndex, lastSlashIndex + 1, ":");

    return sb.toString();
  }

  private String joinSetWithIndentation(Set<String> set) {
    return set.isEmpty() ? "" : '"' + String.join("\",\n        \"", set) + "\",";
  }

  private String joinSetWithIndentationNl(Set<String> set) {
    String rv = joinSetWithIndentation(set);
    return rv.isEmpty() ? rv : "\n        " + rv;
  }

  private String replaceLabelName(String labelPathAndName, String newLabelName) {
    return LABEL_NAME.matcher(labelPathAndName).replaceAll(newLabelName);
  }

  private Set<String> mapJavaGapicDeps(Set<String> protoImports) {
    Set<String> javaImports = new TreeSet<>();
    for (String protoImport : protoImports) {
      if (protoImport.endsWith(":iam_policy_proto") || protoImport.endsWith(":policy_proto")) {
        javaImports.add(replaceLabelName(protoImport, ":iam_java_proto"));
      } else if (protoImport.endsWith(":service_proto")) {
        javaImports.add(replaceLabelName(protoImport, ":api_java_proto"));
      }
    }
    return javaImports;
  }

  private Set<String> mapJavaGapicTestDeps(Set<String> protoImports) {
    Set<String> javaImports = new TreeSet<>();
    for (String protoImport : protoImports) {
      if (protoImport.endsWith(":iam_policy_proto") || protoImport.endsWith(":policy_proto")) {
        javaImports.add(replaceLabelName(protoImport, ":iam_java_grpc"));
      }
    }
    return javaImports;
  }

  private Set<String> mapGoProtoDeps(Set<String> protoImports) {
    Set<String> goImports = new TreeSet<>();

    for (String protoImport : protoImports) {
      if (protoImport.startsWith("@com_google_protobuf//")) {
        continue;
      }

      if (protoImport.endsWith(":resource_proto")
          || protoImport.endsWith(":client_proto")
          || protoImport.endsWith(":field_behavior_proto")
          || protoImport.endsWith(":http_proto")) {
        goImports.add(replaceLabelName(protoImport, ":annotations_go_proto"));
      } else if (protoImport.endsWith(":operations_proto")) {
        goImports.add(replaceLabelName(protoImport, ":longrunning_go_proto"));
      } else if (protoImport.endsWith(":iam_policy_proto")
          || protoImport.endsWith(":policy_proto")) {
        goImports.add(replaceLabelName(protoImport, ":iam_go_proto"));
      } else if (protoImport.endsWith(":config_change_proto")) {
        goImports.add(replaceLabelName(protoImport, ":configchange_go_proto"));
      } else if (protoImport.endsWith(":service_proto") || protoImport.endsWith(":quota_proto")) {
        goImports.add(replaceLabelName(protoImport, ":serviceconfig_go_proto"));
      } else if (protoImport.endsWith(":postal_address_proto")) {
        goImports.add(replaceLabelName(protoImport, ":postaladdress_go_proto"));
      } else if (protoImport.endsWith(":monitored_resource_proto")) {
        goImports.add(replaceLabelName(protoImport, ":monitoredres_go_proto"));
      } else if (protoImport.endsWith(":launch_stage_proto")) {
        goImports.add(replaceLabelName(protoImport, ":api_go_proto"));
      } else {
        goImports.add(protoImport.replaceAll("_proto$", "_go_proto"));
      }
    }
    return goImports;
  }

  private Set<String> mapGoGapicDeps(Set<String> protoImports) {
    Set<String> goImports = new TreeSet<>();

    for (String protoImport : protoImports) {
      if (protoImport.startsWith("@com_google_protobuf//")) {
        if (protoImport.endsWith(":duration_proto")) {
          goImports.add("@io_bazel_rules_go//proto/wkt:duration_go_proto");
        }
        continue;
      }

      if (protoImport.endsWith(":operations_proto")) {
        goImports.add(replaceLabelName(protoImport, ":longrunning_go_gapic"));
        goImports.add(replaceLabelName(protoImport, ":longrunning_go_proto"));
        goImports.add("@com_google_cloud_go//longrunning:go_default_library");
        for (String pi : protoImports) {
          if (pi.startsWith("@com_google_protobuf//")) {
            if (pi.endsWith(":struct_proto")) {
              goImports.add("@io_bazel_rules_go//proto/wkt:struct_go_proto");
            } else if (pi.endsWith(":any_proto")) {
              goImports.add("@io_bazel_rules_go//proto/wkt:any_go_proto");
            }
          }
        }
      } else if (protoImport.endsWith(":iam_policy_proto")
          || protoImport.endsWith(":policy_proto")) {
        goImports.add(replaceLabelName(protoImport, ":iam_go_proto"));
      } else if (protoImport.endsWith(":service_proto")) {
        goImports.add(replaceLabelName(protoImport, ":serviceconfig_go_proto"));
      }
    }
    return goImports;
  }

  Map<String, String> getTokens() {
    return Collections.unmodifiableMap(this.tokens);
  }
}
