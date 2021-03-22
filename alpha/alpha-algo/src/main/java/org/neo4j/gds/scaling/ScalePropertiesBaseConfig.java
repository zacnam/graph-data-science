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
package org.neo4j.gds.scaling;

import org.immutables.value.Value;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.config.AlgoBaseConfig;

import java.util.List;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.AbstractPropertyMappings.fromObject;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

@ValueClass
public interface ScalePropertiesBaseConfig extends AlgoBaseConfig {

    @Configuration.ConvertWith("parsePropertyNames")
    List<String> nodeProperties();

    @Configuration.ConvertWith("org.neo4j.gds.scaling.Scaler.Variant#fromCypher")
    @Configuration.ToMapValue("org.neo4j.gds.scaling.Scaler.Variant#toCypher")
    List<Scaler.Variant> scalers();

    @SuppressWarnings("unused")
    static List<String> parsePropertyNames(Object nodePropertiesOrMappings) {
        if (nodePropertiesOrMappings instanceof List) {
            var nodeProperties = (List<?>) nodePropertiesOrMappings;
            if (nodeProperties.stream().anyMatch(property -> !(property instanceof String))) {
                throw new IllegalArgumentException("nodeProperties must be strings");
            }
            return (List<String>) nodeProperties;
        }
        return fromObject(nodePropertiesOrMappings)
            .mappings()
            .stream()
            .map(PropertyMapping::propertyKey)
            .collect(Collectors.toList());
    }

    @Value.Check
    default void propertiesSizeMustEqualScalersSize() {
        if (scalers().size() != 1 && scalers().size() != nodeProperties().size()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Specify a scaler for each node property. Found %d scalers for %d node properties.",
                scalers().size(),
                nodeProperties().size()
            ));
        }
    }
}
