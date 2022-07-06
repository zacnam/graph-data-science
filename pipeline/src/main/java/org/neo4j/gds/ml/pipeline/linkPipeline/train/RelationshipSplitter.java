/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.ml.pipeline.linkPipeline.train;

import org.neo4j.gds.ElementProjection;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionSplitConfig;
import org.neo4j.gds.ml.splitting.EdgeSplitter;
import org.neo4j.gds.ml.splitting.ImmutableSplitRelationshipsMutateConfig;
import org.neo4j.gds.ml.splitting.SplitRelationships;
import org.neo4j.gds.ml.splitting.SplitRelationshipsBaseConfig;
import org.neo4j.gds.ml.splitting.SplitRelationshipsMutateConfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class RelationshipSplitter {

    private static final String SPLIT_ERROR_TEMPLATE = "%s graph contains no relationships. Consider increasing the `%s` or provide a larger graph";

    private final LinkPredictionSplitConfig splitConfig;
    private final ProgressTracker progressTracker;
    private final GraphStore graphStore;

    RelationshipSplitter(
        GraphStore graphStore,
        LinkPredictionSplitConfig splitConfig,
        ProgressTracker progressTracker
    ) {
        this.graphStore = graphStore;
        this.splitConfig = splitConfig;
        this.progressTracker = progressTracker;
    }

    public void splitRelationships(
        Collection<RelationshipType> relationshipTypes,
        Collection<NodeLabel> nodeLabels,
        Optional<Long> randomSeed,
        Optional<String> relationshipWeightProperty
    ) {
        progressTracker.beginSubTask("Split relationships");

        splitConfig.validateAgainstGraphStore(graphStore);

        var testComplementRelationshipType = RelationshipType.of(splitConfig.testComplementRelationshipType());

        // Relationship sets: test, train, feature-input, test-complement. The nodes are always the same.
        // 1. Split base graph into test, test-complement
        //      Test also includes newly generated negative links, that were not in the base graph (and positive links).
        relationshipSplit(splitConfig.testSplit(randomSeed, relationshipWeightProperty), nodeLabels, relationshipTypes);
        validateTestSplit(graphStore);


        // 2. Split test-complement into (labeled) train and feature-input.
        //      Train relationships also include newly generated negative links, that were not in the base graph (and positive links).
        relationshipSplit(splitConfig.trainSplit(randomSeed, relationshipWeightProperty), nodeLabels, List.of(testComplementRelationshipType));

        graphStore.deleteRelationships(testComplementRelationshipType);

        progressTracker.endSubTask("Split relationships");
    }

    private void validateTestSplit(GraphStore graphStore) {
        String testRelationshipType = splitConfig.testRelationshipType();
        if (graphStore.getGraph(RelationshipType.of(testRelationshipType)).relationshipCount() <= 0) {
            throw new IllegalStateException(formatWithLocale(
                SPLIT_ERROR_TEMPLATE,
                "Test",
                LinkPredictionSplitConfig.TEST_FRACTION_KEY
            ));
        }
    }

    private void relationshipSplit(SplitRelationshipsBaseConfig splitConfig, Collection<NodeLabel> nodeLabels, Collection<RelationshipType> relationshipTypes) {
        var graph = graphStore.getGraph(nodeLabels, relationshipTypes, Optional.ofNullable(splitConfig.relationshipWeightProperty()));

        var splitAlgo = new SplitRelationships(graph, graph, splitConfig);

        EdgeSplitter.SplitResult result = splitAlgo.compute();

        SplitRelationshipMutate.mutate(graphStore, result, splitConfig);
    }

    static MemoryEstimation splitEstimation(LinkPredictionSplitConfig splitConfig, List<String> relationshipTypes, Optional<String> relationshipWeight) {
        List<String> checkRelTypes = relationshipTypes
            .stream()
            .map(type -> type.equals(ElementProjection.PROJECT_ALL) ? RelationshipType.ALL_RELATIONSHIPS.name : type)
            .collect(Collectors.toList());

        SplitRelationshipsMutateConfig testSplitConfig =  ImmutableSplitRelationshipsMutateConfig.builder()
            .from(splitConfig.testSplit(Optional.empty(), relationshipWeight))
            .relationshipTypes(checkRelTypes)
            .build();

        var firstSplitEstimation = MemoryEstimations
            .builder("Test/Test-complement split")
            .add(SplitRelationships.estimate(testSplitConfig))
            .build();

        SplitRelationshipsMutateConfig trainSplitConfig = ImmutableSplitRelationshipsMutateConfig.builder()
            .from(splitConfig.trainSplit(Optional.empty(), relationshipWeight))
            .relationshipTypes(List.of(splitConfig.testComplementRelationshipType()))
            .build();

        var secondSplitEstimation = MemoryEstimations
            .builder("Train/Feature-input split")
            .add(SplitRelationships.estimate(trainSplitConfig))
            .build();

        return MemoryEstimations.builder("Split relationships")
            .add(firstSplitEstimation)
            .add(secondSplitEstimation)
            .build();
    }
}
