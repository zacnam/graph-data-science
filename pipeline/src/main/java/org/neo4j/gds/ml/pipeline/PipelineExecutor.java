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
package org.neo4j.gds.ml.pipeline;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.executor.ExecutionContext;

import java.util.Collection;
import java.util.Map;

import static org.neo4j.gds.config.MutatePropertyConfig.MUTATE_PROPERTY_KEY;

public abstract class PipelineExecutor<
    PIPELINE_CONFIG extends AlgoBaseConfig,
    PIPELINE extends Pipeline<?, ?>,
    RESULT
> extends Algorithm<RESULT> {

    public enum DatasetSplits {
        TRAIN,
        TEST,
        TEST_COMPLEMENT,
        FEATURE_INPUT
    }

    protected final PIPELINE pipeline;
    protected final PIPELINE_CONFIG config;
    protected final ExecutionContext executionContext;
    protected final GraphStore graphStore;
    protected final String graphName;

    public PipelineExecutor(
        PIPELINE pipeline,
        PIPELINE_CONFIG config,
        ExecutionContext executionContext,
        GraphStore graphStore,
        String graphName,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.pipeline = pipeline;
        this.config = config;
        this.executionContext = executionContext;
        this.graphStore = graphStore;
        this.graphName = graphName;
    }

    public abstract Map<DatasetSplits, GraphFilter> splitDataset();

    protected abstract RESULT execute(Map<DatasetSplits, GraphFilter> dataSplits);

    @Override
    public RESULT compute() {
        progressTracker.beginSubTask();

        var dataSplits = splitDataset();
        try {
            var featureInputGraphFilter = dataSplits.get(DatasetSplits.FEATURE_INPUT);

            progressTracker.beginSubTask("execute node property steps");
            executeNodePropertySteps(featureInputGraphFilter);
            progressTracker.endSubTask("execute node property steps");

            this.pipeline.validate(graphStore, config);

            var result = execute(dataSplits);
            progressTracker.endSubTask();
            return result;
        } catch (Exception e) {
            throw e;
        } finally {
            cleanUpGraphStore(dataSplits);
        }
    }

    @Override
    public void release() {

    }

    private void executeNodePropertySteps(GraphFilter graphFilter) {
        for (ExecutableNodePropertyStep step : pipeline.nodePropertySteps()) {
            progressTracker.beginSubTask();
            step.execute(executionContext, graphName, graphFilter.nodeLabels(), graphFilter.relationshipTypes());
            progressTracker.endSubTask();
        }
    }

    protected void cleanUpGraphStore(Map<DatasetSplits, GraphFilter> datasets) {
        removeNodeProperties(graphStore, config.nodeLabelIdentifiers(graphStore));
    }

    private void removeNodeProperties(GraphStore graphstore, Iterable<NodeLabel> nodeLabels) {
        pipeline.nodePropertySteps().forEach(step -> {
            var intermediateProperty = step.config().get(MUTATE_PROPERTY_KEY);
            if (intermediateProperty instanceof String) {
                nodeLabels.forEach(label -> graphstore.removeNodeProperty(label, ((String) intermediateProperty)));
            }
        });
    }

    @ValueClass
    public interface GraphFilter {
        Collection<NodeLabel> nodeLabels();
        Collection<RelationshipType> relationshipTypes();
    }
}
