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
package org.neo4j.gds.collections;

import java.util.function.LongConsumer;

@HugeSparseArray(valueType = double[].class)
public interface HugeSparseDoubleArrayArray {

    long capacity();

    double[] get(long index);

    boolean contains(long index);

    static Builder growingBuilder(double[] defaultValue, LongConsumer trackAllocation) {
        return growingBuilder(defaultValue, trackAllocation, 0);
    }

    static Builder growingBuilder(double[] defaultValue, LongConsumer trackAllocation, long initialCapacity) {
        return new HugeSparseDoubleArrayArraySon.GrowingBuilder(defaultValue, trackAllocation, initialCapacity);
    }

    interface Builder {
        void set(long index, double[] value);

        HugeSparseDoubleArrayArray build();
    }
}
