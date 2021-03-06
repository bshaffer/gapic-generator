/* Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.discogapic;

import com.google.api.codegen.common.TargetLanguage;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Java discovery doc message and client generator baseline tests. */
@RunWith(Parameterized.class)
public class JavaDiscoGapicGeneratorTest extends DiscoGapicTestBase {

  public JavaDiscoGapicGeneratorTest(
      String name,
      String discoveryDocFileName,
      String[] gapicConfigFileNames,
      String packageConfigFileName) {
    super(
        TargetLanguage.JAVA,
        name,
        discoveryDocFileName,
        gapicConfigFileNames,
        packageConfigFileName);
  }

  /**
   * Declares test parameters, each one an array of values passed to the constructor, with the first
   * element a name, the second a discovery doc, and the third a partial GAPIC config.
   */
  @Parameters(name = "{0}")
  public static List<Object[]> testedConfigs() {
    File dir =
        new File(
            System.getProperty("user.dir"),
            "src/test/java/com/google/api/codegen/discogapic/testdata");
    ImmutableList.Builder<Object[]> builder = ImmutableList.<Object[]>builder();
    for (File file : dir.listFiles(new DiscoveryFile("simplecompute"))) {
      String fileName = file.getName();
      builder.add(
          new Object[] {
            "java_" + fileName,
            "testdata/" + fileName,
            new String[] {
              "com/google/api/codegen/discogapic/testdata/simplecompute_gapic.yaml",
            },
            "com/google/api/codegen/discogapic/testdata/simplecompute_pkg2.yaml"
          });
    }
    return builder.build();
  }

  @Before
  public void putTestDirectory() {
    getTestDataLocator().addTestDataSource(this.getClass(), "testdata/java");
  }

  // Tests
  // =====

  @Test
  public void messages() throws Exception {
    test();
  }
}
