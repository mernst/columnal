/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.internal.GeometricDistribution;
import com.pholser.junit.quickcheck.internal.generator.SimpleGenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import test.gen.type.GenDataType;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

// Tests that the GenDataType generator is outputting all classes
public class TestGenDataType
{
    private static enum Container
    {
        TAGGED, RECORD, ARRAY;
    }

    public void testVarieties()
    {
        // We check that each nested pair occurs.  We start by putting them all in a set, remove if we find them,
        // and any left at the end did not occur:
        Set<Pair<Container, Container>> nestings = new HashSet<>();
        nestings.add(new Pair<Container, Container>(null, null));
        for (Container a : Container.values())
        {
            for (Container b : Container.values())
            {
                nestings.add(new Pair<Container, Container>(a, b));
            }
            nestings.add(new Pair<Container, Container>(a, null));
        }

        Random r = new Random(1L);
        GenDataType genDataType = new GenDataType();
        for (int i = 0; i < 1000; i++)
        {
            SourceOfRandomness sourceOfRandomness = new SourceOfRandomness(r);
            DataType t = genDataType.generate(sourceOfRandomness, new SimpleGenerationStatus(new GeometricDistribution(), sourceOfRandomness, 10));
            nestings.removeAll(calculateNesting(t).collect(Collectors.toList()));
        }

        assertEquals(Collections.emptySet(), nestings);
    }

    private static Stream<Pair<Container, Container>> calculateNesting(DataType t)
    {
        try
        {
            return t.apply(new DataTypeVisitor<Stream<Pair<Container, Container>>>()
            {
                @Override
                public Stream<Pair<Container, Container>> number(NumberInfo numberInfo) throws InternalException, UserException
                {
                    return Stream.of(new Pair<Container, Container>(null, null));
                }

                @Override
                public Stream<Pair<Container, Container>> text() throws InternalException, UserException
                {
                    return Stream.of(new Pair<Container, Container>(null, null));
                }

                @Override
                public Stream<Pair<Container, Container>> date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return Stream.of(new Pair<Container, Container>(null, null));
                }

                @Override
                public Stream<Pair<Container, Container>> bool() throws InternalException, UserException
                {
                    return Stream.of(new Pair<Container, Container>(null, null));
                }

                @Override
                public Stream<Pair<Container, Container>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return tags.stream().flatMap(t -> Utility.streamNullable(t.getInner())).<Pair<Container, Container>>flatMap((Function<DataType, Stream<Pair<Container, Container>>>)(t -> TestGenDataType.calculateNesting(t))).map(wrap(Container.TAGGED));
                }

                @Override
                public Stream<Pair<Container, Container>> record(ImmutableMap<String, DataType> fields) throws InternalException, UserException
                {
                    return fields.values().stream().<Pair<Container, Container>>flatMap((Function<DataType, Stream<Pair<Container, Container>>>)TestGenDataType::calculateNesting).map(wrap(Container.RECORD));
                }

                @Override
                public Stream<Pair<Container, Container>> array(DataType inner) throws InternalException, UserException
                {
                    if (inner == null)
                        throw new RuntimeException("Should not generate null inside array");
                    return calculateNesting(inner).map(wrap(Container.ARRAY));
                }
            });
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Function<Pair<Container, Container>, Pair<Container, Container>> wrap(Container outer)
    {
        return (Pair<Container, Container> p) -> new Pair<Container, Container>(outer, p.getFirst());
    }
}
