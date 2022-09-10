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

package test.gen.nonsenseTrans;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.DataTestUtil;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

import java.util.List;

/**
 * Created by neil on 16/11/2016.
 */
public class GenNonsenseSummaryStats extends Generator<Transformation_Mgr>
{
    public GenNonsenseSummaryStats()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        List<ColumnId> splitBy = DataTestUtil.makeList(sourceOfRandomness, 0, 4, () -> TestUtil.generateColumnId(sourceOfRandomness));
        
        try
        {
            DummyManager mgr = new DummyManager();
            List<Pair<ColumnId, Expression>> summaries = DataTestUtil.makeList(sourceOfRandomness, 1, 5, () -> new Pair<>(TestUtil.generateColumnId(sourceOfRandomness),
                new CallExpression(FunctionList.getFunctionLookup(mgr.getUnitManager()),"count", IdentExpression.makeEntireColumnReference(TestUtil.generateTableId(sourceOfRandomness), TestUtil.generateColumnId(sourceOfRandomness)))));
            return new Transformation_Mgr(mgr, new Aggregate(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), ImmutableList.copyOf(summaries), ImmutableList.copyOf(splitBy)));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
