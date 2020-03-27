/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.parser;

import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredBuildTargetFactoryForTests;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.parser.api.RawTargetNode;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BuiltTargetVerifierTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private Cells cell;

  @Before
  public void setUp() {
    cell = new TestCellBuilder().build();
  }

  @Test
  public void testVerificationThrowsWhenUnknownFlavorsArePresent() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    thrown.expect(UnexpectedFlavorException.class);
    thrown.expectMessage(
        "The following flavor(s) are not supported on target //a/b:c#d:\nd\n\n"
            + "Available flavors are:\n\n\n\n"
            + "- Please check the spelling of the flavor(s).\n"
            + "- If the spelling is correct, please check that the related SDK has been installed.");

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        Paths.get("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c#d"),
        new FlavoredDescription(
            new FlavorDomain<>("flavors", ImmutableMap.of(InternalFlavor.of("a"), "b"))),
        RawTargetNode.of(
            ForwardRelativePath.EMPTY,
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of())));
  }

  @Test
  public void testVerificationThrowsWhenDescriptionNotFlavored() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "The following flavor(s) are not supported on target //a/b:c:\nd.\n\n"
            + "Please try to remove them when referencing this target.");

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        Paths.get("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c#d"),
        new NonFlavoredDescription(),
        RawTargetNode.of(
            ForwardRelativePath.EMPTY,
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of())));
  }

  @Test
  public void testVerificationThrowsWhenDataIsMalformed() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        String.format(
            "Attempting to parse build target from malformed raw data in %s: attribute->value.",
            MorePaths.pathWithPlatformSeparators("a/b/BUCK")));

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        Paths.get("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c"),
        new NonFlavoredDescription(),
        RawTargetNode.of(
            ForwardRelativePath.EMPTY,
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of("attribute", "value"))));
  }

  @Test
  public void testVerificationThrowsWhenPathsAreDifferent() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        String.format(
            "Raw data claims to come from [z/y/z], but we tried rooting it at [%s].",
            MorePaths.pathWithPlatformSeparators("a/b")));

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        cell.getRootCell().getRoot().resolve("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c"),
        new NonFlavoredDescription(),
        RawTargetNode.of(
            ForwardRelativePath.of("z/y/z"),
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of("name", "target_name"))));
  }

  @Test
  public void testVerificationThrowsWhenUnflavoredBuildTargetsAreDifferent() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(
        "Inconsistent internal state, target from data: //a/b:target_name, "
            + "expected: //a/b:c, raw data: name->target_name");

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        cell.getRootCell().getRoot().resolve("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c"),
        new NonFlavoredDescription(),
        RawTargetNode.of(
            ForwardRelativePath.of("a/b"),
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of("name", "target_name"))));
  }

  @Test
  public void testVerificationDoesNotFailWithValidFlavoredTargets() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        cell.getRootCell().getRoot().resolve("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c#d"),
        new FlavoredDescription(
            new FlavorDomain<>("flavors", ImmutableMap.of(InternalFlavor.of("d"), "b"))),
        RawTargetNode.of(
            ForwardRelativePath.of("a/b"),
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of("name", "c"))));
  }

  @Test
  public void testVerificationDoesNotFailWithValidUnflavoredTargets() {
    BuiltTargetVerifier builtTargetVerifier = new BuiltTargetVerifier();

    builtTargetVerifier.verifyBuildTarget(
        cell.getRootCell(),
        RuleType.of("build_rule", RuleType.Kind.BUILD),
        cell.getRootCell().getRoot().resolve("a/b/BUCK"),
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c"),
        new NonFlavoredDescription(),
        RawTargetNode.of(
            ForwardRelativePath.of("a/b"),
            "java_library",
            ImmutableList.of(),
            ImmutableList.of(),
            TwoArraysImmutableHashMap.copyOf(ImmutableMap.of("name", "c"))));
  }

  private static class FlavoredDescription
      implements DescriptionWithTargetGraph<BuildRuleArg>, Flavored {

    private final ImmutableSet<FlavorDomain<?>> flavorDomains;

    private FlavoredDescription(FlavorDomain<?>... flavorDomains) {
      this.flavorDomains = ImmutableSet.copyOf(flavorDomains);
    }

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        BuildRuleArg args) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Class<BuildRuleArg> getConstructorArgType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains(
        TargetConfiguration toolchainTargetConfiguration) {
      return Optional.of(flavorDomains);
    }
  }

  private static class NonFlavoredDescription implements DescriptionWithTargetGraph<BuildRuleArg> {

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        BuildRuleArg args) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Class<BuildRuleArg> getConstructorArgType() {
      throw new UnsupportedOperationException();
    }
  }
}
