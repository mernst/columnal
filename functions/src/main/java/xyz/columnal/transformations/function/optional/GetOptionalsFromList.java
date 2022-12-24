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

package xyz.columnal.transformations.function.optional;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import xyz.columnal.transformations.function.ValueFunction1;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;

import java.util.ArrayList;

public class GetOptionalsFromList extends FunctionDefinition
{
    public GetOptionalsFromList() throws InternalException
    {
        super("optional:get optionals from list");
    }
    
    @Override
    public ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction1<ListEx>(ListEx.class) {
            @Override
            public Object call1(ListEx srcList) throws InternalException, UserException
            {
                int size = srcList.size();
                ArrayList<Object> present = new ArrayList<>(size / 8);
                
                for (int i = 0; i < size; i++)
                {
                    TaggedValue taggedValue = Utility.cast(srcList.get(i), TaggedValue.class);
                    if (taggedValue.getTagIndex() == 1 && taggedValue.getInner() != null)
                        present.add(taggedValue.getInner());
                }
                
                return new ListExList(present);
            }
        };
    }
}
