package io.jenkins.plugins.coverage.metrics;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.metric.Coverage.CoverageBuilder;
import edu.hm.hafner.metric.CyclomaticComplexity;
import edu.hm.hafner.metric.FileNode;
import edu.hm.hafner.metric.LinesOfCode;
import edu.hm.hafner.metric.Node;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.FreeStyleProject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.CoverageTool.CoverageParser;

import static edu.hm.hafner.metric.Metric.*;
import static io.jenkins.plugins.coverage.model.Assertions.*;

/**
 * Integration test for delta computation of reference builds.
 */
class DeltaComputationITest extends AbstractCoverageITest {
    @Test
    void shouldComputeDeltaInFreestyleJob() {
        FreeStyleProject project = createFreestyleJob(CoverageParser.JACOCO,
                JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        Run<?, ?> firstBuild = buildSuccessfully(project);
        verifyFirstBuild(firstBuild);

        // update parser pattern to pick only the coding style results
        project.getPublishersList().get(CoverageRecorder.class).getTools().get(0).setPattern(JACOCO_CODING_STYLE_FILE);

        Run<?, ?> secondBuild = buildSuccessfully(project);
        verifySecondBuild(secondBuild);

        verifyDeltaComputation(firstBuild, secondBuild);
    }

    @Test
    void shouldComputeDeltaInPipeline() {
        WorkflowJob job = createPipeline(CoverageParser.JACOCO, JACOCO_ANALYSIS_MODEL_FILE, JACOCO_CODING_STYLE_FILE);

        Run<?, ?> firstBuild = buildSuccessfully(job);
        verifyFirstBuild(firstBuild);

        // update parser pattern to pick only the codingstyle results
        setPipelineScript(job,
                "recordCoverage tools: [[parser: 'JACOCO', pattern: '" + JACOCO_CODING_STYLE_FILE + "']]");

        Run<?, ?> secondBuild = buildSuccessfully(job);
        verifySecondBuild(secondBuild);

        verifyDeltaComputation(firstBuild, secondBuild);
    }

    private static void verifyFirstBuild(final Run<?, ?> firstBuild) {
        var action = firstBuild.getAction(CoverageBuildAction.class);

        var builder = new CoverageBuilder();
        assertThat(action.getAllValues(Baseline.PROJECT)).contains(
                builder.setMetric(LINE)
                        .setCovered(JACOCO_ANALYSIS_MODEL_COVERED + JACOCO_CODING_STYLE_COVERED)
                        .setMissed(JACOCO_ANALYSIS_MODEL_MISSED + JACOCO_CODING_STYLE_MISSED)
                        .build(),
                builder.setMetric(BRANCH)
                        .setCovered(1544 + 109)
                        .setMissed(1865 - (1544 + 109))
                        .build(),
                new LinesOfCode(JACOCO_ANALYSIS_MODEL_TOTAL + JACOCO_CODING_STYLE_TOTAL),
                new CyclomaticComplexity(2718));
    }

    private static void verifySecondBuild(final Run<?, ?> secondBuild) {
        var action = secondBuild.getAction(CoverageBuildAction.class);

        var builder = new CoverageBuilder();
        assertThat(action.getAllValues(Baseline.PROJECT)).contains(
                builder.setMetric(LINE)
                        .setCovered(JACOCO_CODING_STYLE_COVERED)
                        .setMissed(JACOCO_CODING_STYLE_MISSED)
                        .build(),
                builder.setMetric(BRANCH)
                        .setCovered(109)
                        .setMissed(7)
                        .build(),
                new LinesOfCode(JACOCO_CODING_STYLE_TOTAL),
                new CyclomaticComplexity(160));
    }

    /**
     * Verifies the coverageComputation of the first and second build of the job.
     *
     * @param firstBuild
     *         of the project which is used as a reference
     * @param secondBuild
     *         of the project
     */
    private void verifyDeltaComputation(final Run<?, ?> firstBuild, final Run<?, ?> secondBuild) {
        assertThat(secondBuild.getAction(CoverageBuildAction.class)).isNotNull();

        CoverageBuildAction action = secondBuild.getAction(CoverageBuildAction.class);

        assertThat(action).isNotNull();
        assertThat(action.getReferenceBuild())
                .isPresent()
                .satisfies(reference -> assertThat(reference.get()).isEqualTo(firstBuild));

        assertThat(action.formatDelta(Baseline.PROJECT, LINE)).isEqualTo("-4.14%");
        assertThat(action.formatDelta(Baseline.PROJECT, BRANCH)).isEqualTo("+5.33%");
        assertThat(action.formatDelta(Baseline.PROJECT, LOC)).isEqualTo(String.valueOf(-JACOCO_ANALYSIS_MODEL_TOTAL));
        assertThat(action.formatDelta(Baseline.PROJECT, COMPLEXITY)).isEqualTo(String.valueOf(160 - 2718));

        verifyChangeCoverage(action);
    }

    /**
     * Verifies the calculated change coverage including the change coverage delta and the code delta. This makes sure
     * these metrics are set properly even if there are no code changes.
     *
     * @param action
     *         The created Jenkins action
     */
    private void verifyChangeCoverage(final CoverageBuildAction action) {
        Node root = action.getResult();
        assertThat(root).isNotNull();
        assertThat(root.getAllFileNodes()).flatExtracting(FileNode::getChangedLines).isEmpty();
    }
}
