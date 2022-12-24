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

package xyz.columnal.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnStorage.BeforeGet;
import xyz.columnal.data.datatype.ProgressListener;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class CalculatedColumn<S extends ColumnStorage<?>> extends Column implements BeforeGet<S>
{
    public CalculatedColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
    }

    // Preserves type
    /*
    public static CalculatedColumn fold(RecordSet rs, String name, Column src, DataTypeVisitor<FoldOperation> op) throws UserException, InternalException
    {
        return src.getType().apply(new DataTypeVisitorGet<CalculatedColumn>()
        {
            @Override
            public CalculatedColumn number(GetValue<Number> g) throws InternalException, UserException
            {
                return new PrimitiveCalculatedColumn<Number>(rs, name, src, new NumericColumnStorage(0), op.number(), g)
                {
                    private DataType dataType = new DataTypeOf(src.getType())
                    {
                        @Override
                        public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                        {
                            return visitor.number((index, prog) -> {
                                fillCacheWithProgress(index, prog);
                                return cache.get(index);
                            });
                        }
                    };

                    @Override
                    protected void clearCache() throws InternalException
                    {
                        cache = new NumericColumnStorage(0);
                    }

                    @Override
                    public DataType getType()
                    {
                        return dataType;
                    }
                };
            }

            @Override
            public CalculatedColumn text(GetValue<String> g) throws InternalException, UserException
            {
                return new PrimitiveCalculatedColumn<String>(rs, name, src, new StringColumnStorage(), op.text(), g)
                {
                    private DataType dataType = new DataTypeOf(src.getType())
                    {
                        @Override
                        public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                        {
                            return visitor.text((index, prog) -> {
                                fillCacheWithProgress(index, prog);
                                return cache.get(index);
                            });
                        }
                    };

                    @Override
                    protected void clearCache() throws InternalException
                    {
                        cache = new StringColumnStorage();
                    }

                    @Override
                    public DataType getType()
                    {
                        return dataType;
                    }
                };
            }

            @Override
            public CalculatedColumn tagged(int tagIndex, String tag, DataType inner) throws InternalException, UserException
            {
                throw new UnimplementedException("");
            }
        });
    }
*/
    @Override
    public final void beforeGet(S storage, int index, ProgressListener progressListener) throws UserException, InternalException
    {
        if (index < 0 || index >= getLength())
            return; // Will later throw out of bounds problem

        int filled = getCacheFilled();
        // Fetch values:
        while (index >= filled)
        {
            int prevFilled = filled;
            fillNextCacheChunk();
            filled = getCacheFilled();
            if (filled <= prevFilled)
            {
                throw new InternalException("Looking for index " + index + " but cache not growing beyond size " + filled + " length should be: " + getLength());
            }
        }
    }

    protected abstract void fillNextCacheChunk() throws UserException, InternalException;

    protected abstract int getCacheFilled();

    /*
    private static abstract class PrimitiveCalculatedColumn<T> extends CalculatedColumn
    {
        protected ColumnStorage<T> cache;
        protected final FoldOperation<T, T> op;
        private final Column src;
        private GetValue<T> get;
        private int chunkSize = 100;

        public PrimitiveCalculatedColumn(RecordSet rs, String name, Column src, ColumnStorage<T> storage, FoldOperation<T, T> op, GetValue<T> get)
        {
            super(rs, name, src);
            this.src = src;
            this.cache = storage;
            this.op = op;
            this.get = get;
        }


        @Override
        protected int getCacheFilled()
        {
            return cache.filled();
        }

        int nextSource = -1;

        @Override
        protected void fillNextCacheChunk() throws UserException, InternalException
        {
            if (nextSource == -1)
            {
                cache.addAll(op.start());
                nextSource = 0;
            }

            int limit = nextSource + chunkSize;
            int i = nextSource;
            for (; i < limit && src.indexValid(i); i++)
            {
                cache.addAll(op.process(get.getWithProgress(i, null)));
            }
            if (i != nextSource && !src.indexValid(i))
            {
                cache.addAll(op.end());
            }
            nextSource = i;
        }
    }
    */
}
