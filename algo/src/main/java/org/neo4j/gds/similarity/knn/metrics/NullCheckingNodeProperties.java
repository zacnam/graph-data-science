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
package org.neo4j.gds.similarity.knn.metrics;

import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.values.storable.Value;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class NullCheckingNodeProperties implements NodeProperties {

    private final NodeProperties properties;
    private final String name;
    private final IdMap idMap;

    NullCheckingNodeProperties(NodeProperties properties, String name, IdMap idMap) {
        this.properties = properties;
        this.name = name;
        this.idMap = idMap;
    }

    @Override
    public double[] doubleArrayValue(long nodeId) {
        var value = properties.doubleArrayValue(nodeId);
        if (value == null) {
            throw new IllegalArgumentException(formatWithLocale(
                "Missing node property `%s` for node with id `%s`.",
                name,
                idMap.toOriginalNodeId(nodeId)
            ));
        }
        return value;
    }

    @Override
    public float[] floatArrayValue(long nodeId) {
        var value = properties.floatArrayValue(nodeId);
        if (value == null) {
            throw new IllegalArgumentException(formatWithLocale(
                "Missing node property `%s` for node with id `%s`.",
                name,
                idMap.toOriginalNodeId(nodeId)
            ));
        }
        return value;
    }

    @Override
    public long[] longArrayValue(long nodeId) {
        var value = properties.longArrayValue(nodeId);
        if (value == null) {
            throw new IllegalArgumentException(formatWithLocale(
                "Missing node property `%s` for node with id `%s`.",
                name,
                idMap.toOriginalNodeId(nodeId)
            ));
        }
        return value;
    }

    @Override
    public Object getObject(long nodeId) {
        var value = properties.getObject(nodeId);
        if (value == null) {
            throw new IllegalArgumentException(formatWithLocale(
                "Missing node property `%s` for node with id `%s`.",
                name,
                idMap.toOriginalNodeId(nodeId)
            ));
        }
        return value;
    }

    @Override
    public ValueType valueType() {
        return properties.valueType();
    }

    @Override
    public Value value(long nodeId) {
        var value = properties.value(nodeId);
        if (value == null) {
            throw new IllegalArgumentException(formatWithLocale(
                "Missing node property `%s` for node with id `%s`.",
                name,
                idMap.toOriginalNodeId(nodeId)
            ));
        }
        return value;
    }

    @Override
    public long size() {
        return properties.size();
    }
}
